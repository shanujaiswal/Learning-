/**
 * 04_method_level_security_and_custom_authorization.java
 *
 * Demonstrates, with illustrative Spring Security code:
 *     1. @PreAuthorize and @PostAuthorize SpEL examples on a DocumentService,
 *        including @PostFilter for collection-level filtering, and a
 *        bean-delegated expression for logic too complex for inline SpEL.
 *     2. A custom PermissionEvaluator implementation (object-level "does this
 *        principal have THIS permission on THIS domain object" checks) and
 *        the MethodSecurityExpressionHandler bean that registers it.
 *     3. A RoleHierarchy config bean, applied consistently to BOTH method
 *        security and URL-based authorizeHttpRequests rules, per the
 *        theory's explicit warning that registering it on only one leaves
 *        the other layer's checks inconsistent.
 *
 * Covers Theory chapter:
 *     16) Advanced Spring Security OAuth2 and JWT/Theory/04 Method-Level Security and Custom Authorization.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-security
 *     - spring-boot-starter-data-jpa    (for the repository interfaces referenced)
 *     - compilation with the "-parameters" flag (so #documentId etc. can
 *       reference method parameters by name in SpEL without an explicit @P)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080), assuming
 * a controller layer (not shown -- this file focuses on the service-level
 * @PreAuthorize/@PostAuthorize/hasPermission checks themselves) delegates to
 * DocumentService:
 *     curl -X GET http://localhost:8080/api/documents/42 \
 *          -H "Authorization: Bearer <jwt-for-the-document-owner-or-an-admin>"
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

// =============================================================================
// Supporting domain types -- minimal illustrative shapes only
// =============================================================================

class Document {
    private Long id;
    private String ownerId;
    private Long teamId;
    private String content;
    private boolean isPublic;

    public Long getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Long getTeamId() { return teamId; }
    public String getContent() { return content; }
    public boolean isPublic() { return isPublic; }
}

@Repository
interface DocumentRepository {
    Optional<Document> findById(Long id);
    List<Document> findAllByTeamId(Long teamId);
}

@Repository
interface TeamMembershipRepository {
    boolean isMember(String username, Long teamId);
    boolean isEditorOnTeam(String username, Long teamId);
}

// =============================================================================
// SECTION 1 -- @PreAuthorize vs @PostAuthorize vs @PostFilter
// =============================================================================

@Service
class DocumentService {

    private final DocumentRepository documentRepository;

    DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * PRE-check: evaluated from the ARGUMENT alone, BEFORE any repository
     * call happens. Fine here because "allowed to call this at all" can be
     * determined purely from the caller's identity and the supplied ownerId
     * -- no need to look anything up first.
     */
    @PreAuthorize("hasRole('ADMIN') or #ownerId == authentication.name")
    public Document createDraft(String ownerId, String content) {
        Document doc = new Document();
        // ... persist doc ...
        return doc;
    }

    /**
     * POST-check: the decision genuinely depends on data we don't have until
     * AFTER fetching it -- the document's stored owner field, not something
     * the caller supplies as an argument. Safe here specifically BECAUSE
     * getDocument() is READ-ONLY: if the check fails, the caller simply
     * never sees the returned Document, and nothing was mutated in the
     * process of finding that out.
     */
    @PostAuthorize("returnObject.ownerId == authentication.name or hasRole('ADMIN')")
    public Document getDocument(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow();
    }

    /**
     * @PostFilter REMOVES non-matching elements from a returned collection,
     * rather than denying the whole response the way @PostAuthorize would.
     * Use this specifically when the goal is "show me only the documents in
     * this team that I'm allowed to see," not an all-or-nothing decision.
     */
    @PostFilter("filterObject.public or filterObject.ownerId == authentication.name")
    public List<Document> listTeamDocuments(Long teamId) {
        return documentRepository.findAllByTeamId(teamId);
    }

    /**
     * IMPORTANT COUNTER-EXAMPLE -- @PostAuthorize would be a HAZARD here,
     * NOT just a style choice, because this method WRITES. If it were
     * annotated @PostAuthorize("returnObject.ownerId == authentication.name")
     * instead of checked up front, the update would have ALREADY HAPPENED by
     * the time an unauthorized caller's request got denied. The check
     * belongs at @PreAuthorize (or delegated to a bean/PermissionEvaluator,
     * Section 2 below) so an unauthorized caller never triggers the write
     * in the first place.
     */
    @PreAuthorize("@documentAuthz.canEdit(authentication, #documentId)")
    public void updateDocument(Long documentId, String newContent) {
        // ... perform the actual update ...
    }
}

/**
 * A dedicated bean for authorization logic too complex/branchy for a single
 * inline SpEL string -- referenced above via "@documentAuthz.canEdit(...)".
 * Keeping this in a real Java method (rather than an ever-growing SpEL
 * expression) keeps it readable, testable, and debuggable like any other code.
 */
@Component("documentAuthz")
class DocumentAuthorizationLogic {

    private final DocumentRepository documentRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    DocumentAuthorizationLogic(DocumentRepository documentRepository,
                                TeamMembershipRepository teamMembershipRepository) {
        this.documentRepository = documentRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    public boolean canEdit(Authentication authentication, Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return false;
        if (doc.getOwnerId().equals(authentication.getName())) return true;
        return teamMembershipRepository.isEditorOnTeam(authentication.getName(), doc.getTeamId());
    }
}

// =============================================================================
// SECTION 2 -- Custom PermissionEvaluator: object-level permission checks
// =============================================================================
// hasPermission(target, permission) and hasPermission(targetId, targetType,
// permission) do NOTHING by default -- Spring's built-in DenyAllPermissionEvaluator
// rejects everything until a real PermissionEvaluator bean is registered.
// This is the standard, Spring-recognized extension point for "permission on
// a domain object" checks that recur across many methods.

@Component
class DocumentPermissionEvaluator implements PermissionEvaluator {

    private final DocumentRepository documentRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    DocumentPermissionEvaluator(DocumentRepository documentRepository,
                                 TeamMembershipRepository teamMembershipRepository) {
        this.documentRepository = documentRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    // Used as: hasPermission(#document, 'EDIT') -- target object already in hand, no extra fetch needed.
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(targetDomainObject instanceof Document doc)) return false;
        return checkPermission(authentication, doc, permission.toString());
    }

    // Used as: hasPermission(#documentId, 'Document', 'EDIT') -- only an ID is available up front.
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                  String targetType, Object permission) {
        if (!"Document".equals(targetType)) return false;
        Document doc = documentRepository.findById((Long) targetId).orElse(null);
        return doc != null && checkPermission(authentication, doc, permission.toString());
    }

    private boolean checkPermission(Authentication authentication, Document doc, String permission) {
        String username = authentication.getName();
        return switch (permission) {
            case "VIEW" -> doc.isPublic() || doc.getOwnerId().equals(username)
                    || teamMembershipRepository.isMember(username, doc.getTeamId());
            case "EDIT" -> doc.getOwnerId().equals(username)
                    || teamMembershipRepository.isEditorOnTeam(username, doc.getTeamId());
            case "DELETE" -> doc.getOwnerId().equals(username);
            default -> false;
        };
    }
}

@Service
class DocumentPermissionedService {

    @PreAuthorize("hasPermission(#documentId, 'Document', 'EDIT')")
    public void updateDocument(Long documentId, String newContent) {
        // ... perform the actual update, reachable only if hasPermission(...) above allowed it ...
    }

    @PreAuthorize("hasPermission(#documentId, 'Document', 'DELETE')")
    public void deleteDocument(Long documentId) {
        // ... perform the actual delete ...
    }
}

// =============================================================================
// SECTION 3 -- RoleHierarchy: applied consistently to BOTH layers
// =============================================================================
// Without this, an ADMIN who should implicitly be able to do everything a
// MODERATOR can needs EVERY check written as hasAnyRole('ADMIN','MODERATOR')
// rather than just hasRole('MODERATOR') -- tedious and easy to get
// inconsistent as new roles are added. Registering it on only ONE of method
// security / URL security leaves the other layer's checks unaffected --
// both wiring points are shown below deliberately, per the theory's
// explicit warning about this exact gotcha.

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class MethodSecurityConfig {

    /** The single source of truth for the hierarchy -- referenced by BOTH
     *  beans below, so method security and URL security stay consistent. */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MODERATOR")
                .role("MODERATOR").implies("USER")
                .build();
        // Now hasRole('USER') is satisfied by USER, MODERATOR, OR ADMIN --
        // ADMIN and MODERATOR no longer need to be spelled out at every check site.
    }

    /** Wires the hierarchy AND the custom PermissionEvaluator into method-level
     *  (@PreAuthorize/@PostAuthorize/hasPermission) expression evaluation. */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RoleHierarchy roleHierarchy, DocumentPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    /** Wires the SAME hierarchy into the URL-based SecurityFilterChain's
     *  authorizeHttpRequests(...) hasRole(...) checks -- omitting this half
     *  is exactly the "applied to method security only" gotcha the theory warns about. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoleHierarchy roleHierarchy) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/moderation/**").hasRole("MODERATOR")   // now ALSO satisfied by ADMIN, thanks to the hierarchy
                    .anyRequest().authenticated()
            );

        // In a real project using WebExpressionAuthorizationManager or an
        // equivalent expression-handler hook on the HttpSecurity DSL, the
        // same RoleHierarchy bean is supplied here so hasRole(...) checks in
        // authorizeHttpRequests(...) respect it identically to the
        // method-security side configured above.
        return http.build();
    }
}

/*
 * NOTE on the annotations/classes used above:
 * This snippet uses real Spring Security method-security types
 * (@PreAuthorize, @PostAuthorize, @PostFilter, PermissionEvaluator,
 * RoleHierarchy, MethodSecurityExpressionHandler, etc.) exactly as they'd
 * appear in a real Spring Boot project, which requires
 * spring-boot-starter-security, @EnableMethodSecurity, compilation with
 * "-parameters" (for #paramName SpEL references), and a
 * @SpringBootApplication entry point to compile and run this end-to-end.
 * DocumentRepository/TeamMembershipRepository are shown as bare interfaces
 * for illustration -- a real project backs them with Spring Data JPA (or
 * another persistence mechanism) and real query methods.
 */
