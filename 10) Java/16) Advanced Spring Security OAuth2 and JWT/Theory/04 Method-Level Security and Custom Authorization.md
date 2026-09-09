# Beyond `hasRole('ADMIN')` -- Why This File Exists

--> The basics file introduced `@PreAuthorize("hasRole('ADMIN')")` and one SpEL expression referencing a method argument. That covers simple role checks. Real authorization requirements are usually messier -- "the owner of this resource, or an admin, or someone with a specific delegated permission granted just for this one document" -- which is where `@PostAuthorize`, custom `PermissionEvaluator` beans, role hierarchies, and the RBAC-vs-ABAC design question this file covers actually earn their place.

# `@PreAuthorize` vs `@PostAuthorize` -- Before vs After the Method Runs

--> Both require `@EnableMethodSecurity` (as covered in the basics file) and both express a SpEL boolean condition -- the difference is WHEN that condition is evaluated relative to the method body actually executing.

| | `@PreAuthorize` | `@PostAuthorize` |
|---|---|---|
| Evaluated | BEFORE the method runs | AFTER the method runs, with its return value available |
| Can reference | Method arguments, `authentication` | Method arguments, `authentication`, AND `returnObject` (the actual return value) |
| Typical use | "Can this principal call this method AT ALL, given the arguments?" | "Given what this method actually returned, was the principal allowed to see/have THIS specific result?" |
| Failure behavior | Method body never executes at all | Method body already ran (including any side effects/writes!) before the check fails |

```java
@Service
public class DocumentService {

    // PRE-check: evaluated from the ARGUMENT alone, before any repository call happens.
    // Fine when "allowed to call this" can be determined purely from input.
    @PreAuthorize("hasRole('ADMIN') or #ownerId == authentication.name")
    public Document createDraft(String ownerId, String content) { ... }

    // POST-check: the decision genuinely depends on data we don't have until AFTER
    // fetching it -- here, the document's stored owner field, not something the
    // caller supplies as an argument at all.
    @PostAuthorize("returnObject.ownerId == authentication.name or hasRole('ADMIN')")
    public Document getDocument(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow();
    }
}
```

--> **`@PostAuthorize` on a MUTATING method is a real hazard, not just a style choice** -- if the annotated method performs a write, a call, or any side effect, that side effect has ALREADY HAPPENED by the time the post-check denies access. Reserve `@PostAuthorize` for read-only methods where "the caller sees a `AccessDeniedException` instead of the data" is an acceptable failure mode; for anything that mutates state, push the check to `@PreAuthorize` (or into the method body) so an unauthorized caller never gets to trigger the write in the first place.
--> **`@PostAuthorize` doesn't filter collections, it's all-or-nothing on the whole return value** -- for filtering which ELEMENTS of a returned list a caller may see, use `@PostFilter` instead (`@PostFilter("filterObject.ownerId == authentication.name")` removes non-matching elements from a returned `Collection` rather than denying the whole response).

# Common SpEL Expressions Reference

| Expression | Meaning |
|---|---|
| `hasRole('ADMIN')` | Principal has authority `ROLE_ADMIN` |
| `hasAnyRole('ADMIN','MODERATOR')` | Principal has at least one of the listed roles |
| `hasAuthority('orders:write')` | Principal has this exact authority string (no `ROLE_` prefixing, unlike `hasRole`) |
| `authentication.name` | The authenticated principal's username/identifier |
| `authentication.principal` | The full principal object (often a `UserDetails` or `Jwt`) |
| `#argName` | References a method parameter by name (requires `-parameters` compiler flag or explicit `@P("argName")`) |
| `returnObject` | (PostAuthorize only) the method's actual return value |
| `filterObject` | (PreFilter/PostFilter only) the current element being evaluated during collection filtering |
| `principal.id == #userId` | A common "resource ownership" pattern comparing the authenticated principal's own ID to an argument |
| `@myBean.someMethod(#arg)` | Delegates the decision to a Spring bean method -- see custom logic below |

## Delegating to a Spring Bean for Logic Too Complex for Inline SpEL

--> Inline SpEL is fine for simple comparisons, but authorization logic that needs a database lookup, or multiple conditions with real branching, belongs in a dedicated bean referenced via `@beanName.method(...)` rather than crammed into a longer and longer SpEL string.

```java
@Component("documentAuthz")
public class DocumentAuthorizationLogic {

    private final DocumentRepository documentRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public DocumentAuthorizationLogic(DocumentRepository documentRepository,
                                       TeamMembershipRepository teamMembershipRepository) {
        this.documentRepository = documentRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    public boolean canEdit(Authentication authentication, Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return false;
        if (doc.getOwnerId().equals(authentication.getName())) return true;
        // Real, multi-step logic that would be unreadable crammed into one SpEL string.
        return teamMembershipRepository.isEditorOnTeam(authentication.getName(), doc.getTeamId());
    }
}

@Service
public class DocumentService {

    @PreAuthorize("@documentAuthz.canEdit(authentication, #documentId)")
    public void updateDocument(Long documentId, String newContent) { ... }
}
```

# Custom `PermissionEvaluator` -- Object-Level Permission Checks

--> Spring Security exposes two special SpEL functions, `hasPermission(target, permission)` and `hasPermission(targetId, targetType, permission)`, but they do NOTHING by default until you register a `PermissionEvaluator` bean implementing the actual logic. This is the standard mechanism for "does this principal have THIS permission on THIS specific domain object" checks that recur across many methods, centralizing the logic in one place instead of repeating bean-delegation expressions everywhere.

```java
@Component
public class DocumentPermissionEvaluator implements PermissionEvaluator {

    private final DocumentRepository documentRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public DocumentPermissionEvaluator(DocumentRepository documentRepository,
                                        TeamMembershipRepository teamMembershipRepository) {
        this.documentRepository = documentRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    // Used as: hasPermission(#document, 'EDIT')  -- target object already in hand
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(targetDomainObject instanceof Document doc)) return false;
        return checkPermission(authentication, doc, permission.toString());
    }

    // Used as: hasPermission(#documentId, 'Document', 'EDIT') -- only an ID is available,
    // e.g. because the object hasn't been loaded yet and you want to avoid a needless fetch
    // when the answer can be short-circuited (though here we still need to load it to check).
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
```

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    // Registers the custom evaluator so hasPermission(...) in SpEL actually
    // delegates to it, instead of the built-in DenyAllPermissionEvaluator
    // (Spring's default, which -- true to its name -- denies everything).
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            DocumentPermissionEvaluator permissionEvaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}

@Service
public class DocumentService {

    @PreAuthorize("hasPermission(#documentId, 'Document', 'EDIT')")
    public void updateDocument(Long documentId, String newContent) { ... }

    @PreAuthorize("hasPermission(#documentId, 'Document', 'DELETE')")
    public void deleteDocument(Long documentId) { ... }
}
```

--> **`PermissionEvaluator` vs a plain delegated bean method -- when to reach for which** -- a `PermissionEvaluator` is the STANDARD, Spring-recognized extension point specifically for object-level "permission on a domain object" checks, which makes intent clearer to other developers reading `hasPermission(...)` in an annotation and keeps ALL such checks behind one consistent interface. A plain `@component.method(...)` bean delegation (shown earlier) is more flexible for logic that doesn't fit the "permission on one object" shape at all (e.g. a multi-object business rule, or a check unrelated to any single domain entity).

# Role Hierarchy -- Avoiding Redundant Role Checks

--> Without a role hierarchy, an ADMIN who should implicitly be able to do everything a MODERATOR can (and a MODERATOR everything a USER can) needs EVERY check written as `hasAnyRole('ADMIN','MODERATOR')` rather than just `hasRole('MODERATOR')` -- tedious and easy to forget to update consistently as new roles are added. A `RoleHierarchy` bean fixes this centrally.

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MODERATOR")
                .role("MODERATOR").implies("USER")
                .build();
        // Now hasRole('USER') is satisfied by USER, MODERATOR, OR ADMIN --
        // ADMIN and MODERATOR no longer need to be spelled out at every check site.
    }

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
```

--> **This must be applied to BOTH method security and URL-based `authorizeHttpRequests` checks to have a consistent effect** -- registering it only on the method-security expression handler leaves the `SecurityFilterChain`'s own `hasRole(...)` calls unaffected by the hierarchy; a matching `RoleHierarchy`-aware configuration is needed on that side too if both mechanisms are in use (as the basics file recommends combining).

# RBAC vs ABAC -- Two Different Authorization Models

--> Nearly everything shown so far (`hasRole`, role hierarchy) is **Role-Based Access Control (RBAC)** -- permission is a function of a STATIC ROLE assigned to the principal ("ADMINs can delete users"). The `PermissionEvaluator` examples above, checking ownership/team-membership dynamically per-object, edge into **Attribute-Based Access Control (ABAC)** -- permission is a function of ATTRIBUTES of the principal, the resource, and sometimes the environment, evaluated per-request rather than baked into a fixed role.

| | RBAC | ABAC |
|---|---|---|
| Permission determined by | A role assigned to the user (ADMIN, USER, MODERATOR) | Attributes of user + resource + context (owner, department, time of day, resource sensitivity, request origin) |
| Typical rule | "ADMINs can delete any document" | "A user can delete a document if they own it, OR are on its team with EDITOR role, AND it's not marked as legally held" |
| Simplicity | Easy to reason about and audit -- a fixed, enumerable set of roles | More expressive, but rules can become complex and harder to audit as attribute combinations grow |
| Where it fits in Spring Security | `hasRole`/`hasAuthority`, role hierarchy | Custom `PermissionEvaluator`, bean-delegated SpEL, or a dedicated policy engine for very complex rule sets |
| Scaling concern | A new access pattern often means a new role (role explosion as requirements grow) | A new access pattern is often just a new attribute combination -- but the accumulated rule complexity can itself become hard to audit |

--> **Most real Spring Security applications use BOTH, layered** -- coarse RBAC at the URL/`hasRole` level for broad gatekeeping ("must at least be a logged-in USER to reach `/api/documents/**` at all"), with finer ABAC-style object-level checks (`PermissionEvaluator`, ownership comparisons) for the "which SPECIFIC resources" question RBAC alone can't answer. This mirrors exactly the layered URL-rules-plus-`@PreAuthorize` pattern the basics file already recommended, just carried one level deeper into per-object attribute checks.
--> **When ABAC-style rules get complex enough that a `PermissionEvaluator` bean starts feeling like a rules engine in disguise**, that's often a signal to consider an actual externalized policy engine (OPA/Open Policy Agent is the common choice in the Java ecosystem) rather than continuing to grow Java `if`/`switch` logic indefinitely -- outside this file's scope, but worth knowing the escape hatch exists.

# Common Gotchas

--> **Using `@PostAuthorize` on a method with side effects** -- the side effect (write, external call, email sent) already happened before the check can deny it; reserve `@PostAuthorize` for read-only methods.
--> **Forgetting to register a `PermissionEvaluator` bean** -- `hasPermission(...)` silently uses Spring's default `DenyAllPermissionEvaluator`, meaning every such check fails, which can look like "the method just never works" rather than an obviously missing configuration.
--> **Applying a `RoleHierarchy` bean to method security only** -- URL-based `authorizeHttpRequests` checks won't respect the hierarchy unless configured separately, leading to inconsistent behavior between the two layers.
--> **Letting inline SpEL expressions grow long and unreadable** -- once a check needs more than a simple comparison or two, move the logic into a delegated bean method or `PermissionEvaluator` instead of an ever-growing SpEL string.
--> **Confusing `hasRole` and `hasAuthority` prefixing** -- `hasRole('X')` checks for `ROLE_X`; `hasAuthority('X')` checks for the literal string `X` with no prefix added. Mixing these up (e.g. calling `hasAuthority('ADMIN')` when the granted authority is actually `ROLE_ADMIN`) silently never matches.
--> **Role explosion in a pure-RBAC design** -- adding a new role for every new fine-grained access pattern instead of introducing an attribute-based check where one would fit more naturally.

# Best Practices Summary

--> Use `@PreAuthorize` by default; reach for `@PostAuthorize` only for read-only methods where the check genuinely depends on the return value.
--> Use `@PostFilter`/`@PreFilter` (not `@PostAuthorize`) when the goal is filtering elements out of a collection rather than allow/deny on the whole response.
--> Move authorization logic more complex than a simple comparison into a delegated bean method or a registered `PermissionEvaluator`, not an ever-longer SpEL string.
--> Register a `RoleHierarchy` consistently across both method security and URL-based rules if using one at all.
--> Default to RBAC for coarse, role-shaped rules; layer in ABAC-style object-level checks (via `PermissionEvaluator`) for "which specific resource" questions RBAC can't answer alone.
--> Watch for role explosion as a signal that some access patterns would be better modeled as attributes than as new roles.
--> Consider an externalized policy engine only once hand-written ABAC logic has genuinely outgrown what a `PermissionEvaluator` can cleanly express.
