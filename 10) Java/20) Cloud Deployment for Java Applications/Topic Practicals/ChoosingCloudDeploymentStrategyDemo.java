/**
 * ChoosingCloudDeploymentStrategyDemo.java
 *
 * Illustrates:
 *     A small, self-contained decision-helper: a WorkloadProfile value
 *     describing the shape of a Java workload, and a
 *     CloudDeploymentStrategyAdvisor that recommends SERVERLESS (FaaS),
 *     CONTAINER, or VM_PAAS based on the heuristics from the Theory
 *     chapter's "Serverless vs. Container vs. VM/PaaS -- Decision Factors"
 *     table (traffic pattern, latency sensitivity, statefulness, team
 *     container expertise, portability needs, GraalVM native-image
 *     availability).
 *
 *     This is a teaching aid, NOT a production decision engine -- real
 *     architectural choices should weigh far more context (existing cloud
 *     spend/contracts, team expertise, compliance requirements, actual
 *     modeled cost at realistic traffic volumes) than any simple if/else
 *     heuristic could capture. See the decision factors table and
 *     "Consolidated Decision Checklist" reproduced below for the full
 *     nuance.
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/04 Choosing a Cloud
 *     Deployment Strategy for Java.md
 *
 * IMPORTANT -- THIS FILE'S main() METHOD DOES COMPILE AND RUN STANDALONE.
 *     It uses only plain Java (no cloud SDK/dependency needed) since it is a
 *     decision-helper/comparison illustration, not a deployment client.
 *     (Unlike files 01-03, there is no infrastructure requirement here.)
 *
 * ---------------------------------------------------------------------------
 * QUICK-REFERENCE SUMMARY OF THE THEORY CHAPTER'S DECISION FACTORS TABLE
 * ---------------------------------------------------------------------------
 *
 *   Factor                    | Favors Serverless (FaaS) | Favors Container    | Favors VM/PaaS
 *   ---------------------------|---------------------------|----------------------|--------------------
 *   Traffic pattern            | Sporadic, bursty          | Steady-to-bursty     | Steady, predictable
 *   Latency sensitivity        | Tolerant of cold starts    | Consistently low     | Consistently low
 *   State/connections          | Stateless, short-lived     | Long-lived OK        | Same as containers
 *   Team container expertise   | Not required               | Required (Docker)    | Minimal
 *   Portability across clouds  | Low                        | High                 | Low-to-medium
 *   Fine-grained OS control    | None                       | Full                 | Partial-to-full
 *   Startup/warm-up tolerance  | Must tolerate/pay for warm | JVM stays warm       | JVM stays warm
 *
 * ---------------------------------------------------------------------------
 * ONE-SENTENCE MENTAL MODEL FOR EACH (from the Theory chapter)
 * ---------------------------------------------------------------------------
 *   SERVERLESS (FaaS) -- pay per invocation, scales to zero, but pays a real
 *                         JVM cold-start tax unless mitigated (SnapStart,
 *                         Premium/min-instances, or GraalVM native images).
 *   CONTAINER          -- "just a Dockerfile," portable across all three
 *                          clouds via Kubernetes, JVM stays warm once a
 *                          Task/Pod is running -- the default for steady,
 *                          latency-sensitive, user-facing Java services.
 *   VM/PAAS            -- least AWS/Azure/GCP-specific ceremony (upload a
 *                          JAR/WAR, the platform manages the rest), but
 *                          usually keeps at least one instance running
 *                          continuously (no scale-to-zero).
 *
 * ---------------------------------------------------------------------------
 * KEY TAKEAWAY (also from the Theory chapter)
 * ---------------------------------------------------------------------------
 *   The decision that matters most is the SHAPE of the workload (serverless
 *   vs. container vs. VM/PaaS), decided by traffic pattern and latency
 *   sensitivity first -- WHICH cloud provider hosts it is usually a
 *   secondary, organizational decision. Whichever shape is chosen, apply the
 *   twelve-factor principles (externalize config, stay stateless/disposable,
 *   log to stdout) -- they are what make any of the shapes actually work
 *   reliably at cloud scale.
 */

package com.example.cloud.decision;

import java.util.ArrayList;
import java.util.List;

public class ChoosingCloudDeploymentStrategyDemo {

    public static void main(String[] args) {
        // Scenario 1: a synchronous, user-facing REST API expected to see
        // steady, sustained traffic all day, holding a small in-memory
        // connection pool -- should recommend CONTAINER (Fargate/Cloud
        // Run/AKS-Cloud-Run-style), per the Theory chapter's "practical
        // starting heuristic for a NEW Java service."
        WorkloadProfile customerFacingApi = new WorkloadProfile(
                /* trafficPattern              */ TrafficPattern.STEADY_SUSTAINED,
                /* latencySensitive            */ true,
                /* holdsLongLivedState         */ true,
                /* teamHasContainerExpertise   */ true,
                /* crossCloudPortabilityNeeded */ false,
                /* graalvmNativeImageViable    */ false
        );

        // Scenario 2: a nightly batch job / webhook handler that fires a
        // handful of times an hour, fully stateless, background/async so
        // occasional cold starts are invisible -- should recommend
        // SERVERLESS.
        WorkloadProfile nightlyBatchWebhook = new WorkloadProfile(
                /* trafficPattern              */ TrafficPattern.SPORADIC_EVENT_DRIVEN,
                /* latencySensitive            */ false,
                /* holdsLongLivedState         */ false,
                /* teamHasContainerExpertise   */ false,
                /* crossCloudPortabilityNeeded */ false,
                /* graalvmNativeImageViable    */ false
        );

        // Scenario 3: a small internal admin tool -- a team with no Docker
        // experience wants to upload a JAR and be done with it, and doesn't
        // want to write a Dockerfile at all -- should recommend VM_PAAS
        // (Elastic Beanstalk / App Service / App Engine Standard).
        WorkloadProfile internalAdminTool = new WorkloadProfile(
                /* trafficPattern              */ TrafficPattern.STEADY_SUSTAINED,
                /* latencySensitive            */ false,
                /* holdsLongLivedState         */ false,
                /* teamHasContainerExpertise   */ false,
                /* crossCloudPortabilityNeeded */ false,
                /* graalvmNativeImageViable    */ false
        );

        // Scenario 4: a latency-sensitive, user-facing HTTP API that is also
        // genuinely bursty/unpredictable, but the team has already invested
        // in Spring Native/GraalVM AOT compilation -- with cold starts
        // brought down to near-Go levels, SERVERLESS becomes viable again
        // even though latency matters, per the Theory chapter's "GraalVM
        // native images as a wildcard" callout.
        WorkloadProfile burstyApiWithGraalvm = new WorkloadProfile(
                /* trafficPattern              */ TrafficPattern.SPORADIC_EVENT_DRIVEN,
                /* latencySensitive            */ true,
                /* holdsLongLivedState         */ false,
                /* teamHasContainerExpertise   */ true,
                /* crossCloudPortabilityNeeded */ false,
                /* graalvmNativeImageViable    */ true
        );

        CloudDeploymentStrategyAdvisor advisor = new CloudDeploymentStrategyAdvisor();

        for (WorkloadProfile profile : List.of(
                customerFacingApi, nightlyBatchWebhook, internalAdminTool, burstyApiWithGraalvm)) {
            Recommendation recommendation = advisor.recommend(profile);
            System.out.println("=================================================");
            System.out.println("Workload    : " + profile);
            System.out.println("Recommended : " + recommendation.strategy());
            System.out.println("Reasoning   :");
            for (String reason : recommendation.reasons()) {
                System.out.println("  - " + reason);
            }
        }
    }

    // =========================================================================
    // Domain model describing the SHAPE of a workload's needs -- deliberately
    // mirrors the "Serverless vs. Container vs. VM/PaaS -- Decision Factors"
    // table dimensions from the Theory chapter.
    // =========================================================================
    enum TrafficPattern {
        SPORADIC_EVENT_DRIVEN,  // occasional, bursty, or unpredictable
        STEADY_SUSTAINED        // steady-to-bursty, moderate-to-high, predictable volume
    }

    enum DeploymentStrategy {
        SERVERLESS, CONTAINER, VM_PAAS
    }

    // Plain final-field value classes (rather than Java 16+ records) so this
    // file compiles on any Java 8+ toolchain, not just modern JDKs.
    static final class WorkloadProfile {
        private final TrafficPattern trafficPattern;
        private final boolean latencySensitive;
        private final boolean holdsLongLivedState;
        private final boolean teamHasContainerExpertise;
        private final boolean crossCloudPortabilityNeeded;
        private final boolean graalvmNativeImageViable;

        WorkloadProfile(TrafficPattern trafficPattern, boolean latencySensitive,
                boolean holdsLongLivedState, boolean teamHasContainerExpertise,
                boolean crossCloudPortabilityNeeded, boolean graalvmNativeImageViable) {
            this.trafficPattern = trafficPattern;
            this.latencySensitive = latencySensitive;
            this.holdsLongLivedState = holdsLongLivedState;
            this.teamHasContainerExpertise = teamHasContainerExpertise;
            this.crossCloudPortabilityNeeded = crossCloudPortabilityNeeded;
            this.graalvmNativeImageViable = graalvmNativeImageViable;
        }

        TrafficPattern trafficPattern() { return trafficPattern; }
        boolean latencySensitive() { return latencySensitive; }
        boolean holdsLongLivedState() { return holdsLongLivedState; }
        boolean teamHasContainerExpertise() { return teamHasContainerExpertise; }
        boolean crossCloudPortabilityNeeded() { return crossCloudPortabilityNeeded; }
        boolean graalvmNativeImageViable() { return graalvmNativeImageViable; }

        @Override
        public String toString() {
            return "WorkloadProfile{trafficPattern=" + trafficPattern
                    + ", latencySensitive=" + latencySensitive
                    + ", holdsLongLivedState=" + holdsLongLivedState
                    + ", teamHasContainerExpertise=" + teamHasContainerExpertise
                    + ", crossCloudPortabilityNeeded=" + crossCloudPortabilityNeeded
                    + ", graalvmNativeImageViable=" + graalvmNativeImageViable + "}";
        }
    }

    static final class Recommendation {
        private final DeploymentStrategy strategy;
        private final List<String> reasons;

        Recommendation(DeploymentStrategy strategy, List<String> reasons) {
            this.strategy = strategy;
            this.reasons = reasons;
        }

        DeploymentStrategy strategy() { return strategy; }
        List<String> reasons() { return reasons; }
    }

    // =========================================================================
    // The heuristic itself -- directly encodes the "Serverless vs. Container
    // vs. VM/PaaS -- Decision Factors" table and the "practical starting
    // heuristic for a NEW Java service" from the Theory chapter, checked in
    // priority order.
    // =========================================================================
    static class CloudDeploymentStrategyAdvisor {

        Recommendation recommend(WorkloadProfile w) {
            List<String> reasons = new ArrayList<>();

            // Rule 1: genuine cross-cloud portability is a hard constraint --
            // per the Theory chapter, containers + Kubernetes are "the only
            // shape that's near-identical across all three clouds," so this
            // wins regardless of the other factors.
            if (w.crossCloudPortabilityNeeded()) {
                reasons.add("Cross-cloud portability is a real requirement -- Docker + Kubernetes "
                        + "is the only one of the three shapes that stays near-identical across "
                        + "AWS/Azure/GCP (EKS/AKS/GKE all run the same manifests).");
                return new Recommendation(DeploymentStrategy.CONTAINER, reasons);
            }

            // Rule 2: sporadic/event-driven traffic AND state that is
            // stateless/short-lived is FaaS's sweet spot -- but only if
            // latency tolerance or a cold-start mitigation (GraalVM native
            // image) makes the JVM warm-up tax acceptable, per the Theory
            // chapter's "Java's JVM warm-up cost tilts this decision more
            // than it does for other languages" callout.
            if (w.trafficPattern() == TrafficPattern.SPORADIC_EVENT_DRIVEN && !w.holdsLongLivedState()) {
                if (!w.latencySensitive()) {
                    reasons.add("Traffic is sporadic/event-driven and the workload is stateless -- "
                            + "background/async work like this is where Java on FaaS (Lambda/Azure "
                            + "Functions/Cloud Functions) is genuinely comfortable without special tuning.");
                    reasons.add("Occasional cold-start latency on an infrequent invocation is invisible "
                            + "to any human waiting -- no mitigation required.");
                    return new Recommendation(DeploymentStrategy.SERVERLESS, reasons);
                }
                if (w.graalvmNativeImageViable()) {
                    reasons.add("Traffic is sporadic and latency-sensitive, but a GraalVM native image "
                            + "build is viable -- this brings Java's cold-start numbers down to "
                            + "near-Go levels (tens of milliseconds), which the Theory chapter calls "
                            + "out as the wildcard that makes FaaS viable even for latency-sensitive "
                            + "sporadic workloads.");
                    reasons.add("Without GraalVM here, SnapStart/Premium-plan/min-instances would be "
                            + "the fallback mitigations to consider before ruling out serverless.");
                    return new Recommendation(DeploymentStrategy.SERVERLESS, reasons);
                }
                reasons.add("Traffic is sporadic, but the workload is latency-sensitive and no "
                        + "cold-start mitigation (SnapStart, Premium/min-instances, GraalVM native "
                        + "image) is confirmed viable -- defaulting to a container keeps the JVM warm "
                        + "and avoids gambling user-facing latency on an unmitigated cold start.");
                reasons.add("Revisit serverless once a mitigation is in place -- the sporadic traffic "
                        + "pattern still favors it on cost grounds.");
                return new Recommendation(DeploymentStrategy.CONTAINER, reasons);
            }

            // Rule 3: steady/predictable traffic, latency-sensitive or
            // holding long-lived state, with a team that already has
            // container expertise -- containers (ECS/Fargate, Cloud Run,
            // AKS/GKE) are the Theory chapter's default recommendation for
            // "a synchronous, user-facing API expected to see sustained
            // traffic."
            if (w.trafficPattern() == TrafficPattern.STEADY_SUSTAINED
                    && (w.latencySensitive() || w.holdsLongLivedState())
                    && w.teamHasContainerExpertise()) {
                if (w.latencySensitive()) {
                    reasons.add("Latency-sensitive with steady traffic -- a container's JVM stays warm "
                            + "once a Task/Pod is running, avoiding FaaS's cold-start tax entirely.");
                }
                if (w.holdsLongLivedState()) {
                    reasons.add("Workload holds long-lived connections/in-memory state (e.g. a "
                            + "HikariCP pool, an in-process cache) -- a container process that stays "
                            + "running fits this naturally, unlike FaaS's short-lived execution model.");
                }
                reasons.add("Team already has Docker expertise -- no new tooling investment required "
                        + "to adopt ECS/Fargate, Cloud Run, or AKS/GKE.");
                return new Recommendation(DeploymentStrategy.CONTAINER, reasons);
            }

            // Rule 4: default -- steady traffic, no strong latency/state
            // pressure forcing a container, and (often) a team that
            // specifically wants to avoid writing a Dockerfile at all --
            // reach for a managed PaaS (Elastic Beanstalk/App Service/App
            // Engine Standard), per the Theory chapter's "reach for a
            // managed PaaS specifically when the team wants to avoid writing
            // a Dockerfile at all" guidance.
            if (!w.teamHasContainerExpertise()) {
                reasons.add("Team has no strong Docker/container expertise and the workload's traffic "
                        + "is steady/predictable -- a managed PaaS (Elastic Beanstalk / App Service / "
                        + "App Engine Standard) lets them 'upload a JAR' and let the platform manage "
                        + "scaling, load balancing, and OS patching.");
            } else {
                reasons.add("Steady, predictable traffic with no strong latency or long-lived-state "
                        + "pressure -- a managed PaaS is the lowest-ceremony option here, even though "
                        + "the team could also run this as a container if they preferred consistency "
                        + "with their other services.");
            }
            reasons.add("Note: unlike Cloud Run/Fargate-with-scale-to-zero, most managed PaaS tiers "
                    + "(Elastic Beanstalk, App Service Basic+, App Engine Flexible) keep at least one "
                    + "instance running continuously -- model idle cost accordingly.");
            return new Recommendation(DeploymentStrategy.VM_PAAS, reasons);
        }
    }
}

/*
 * ---------------------------------------------------------------------------
 * TWELVE-FACTOR APP PRINCIPLES FOR JAVA DEPLOYMENTS -- SUMMARY (from Theory
 * chapter 04's "Twelve-Factor App Principles for Java Deployments" section)
 * ---------------------------------------------------------------------------
 * Whichever deployment shape the advisor above recommends, the Theory
 * chapter is explicit that these twelve factors are what make it actually
 * work reliably at cloud scale -- most "why did my deploy misbehave"
 * mysteries map to one specific factor being skipped:
 *
 *   I.    Codebase            -- one repo, many deploys (dev/staging/prod)
 *                                 from the SAME artifact/image.
 *   II.   Dependencies        -- declare all dependencies explicitly
 *                                 (pom.xml/build.gradle); never rely on JARs
 *                                 pre-installed on the host.
 *   III.  Config               -- store config (DB URLs, credentials, flags)
 *                                 in the ENVIRONMENT, never hardcoded in code.
 *   IV.   Backing services     -- treat a DB/cache/queue as an attached,
 *                                 swappable-by-config RESOURCE.
 *   V.    Build, release, run  -- strictly separate build (compile JAR) from
 *                                 release (JAR + config) from run (start it).
 *   VI.   Processes            -- run as STATELESS processes; persist state
 *                                 only in a backing service.
 *   VII.  Port binding         -- the app is self-contained and exports HTTP
 *                                 via its own port binding (embedded server).
 *   VIII. Concurrency          -- scale OUT via more process instances
 *                                 (horizontal), not one bigger process.
 *   IX.   Disposability        -- fast startup, graceful shutdown; the
 *                                 platform can kill/restart at any moment.
 *   X.    Dev/prod parity      -- keep dev, staging, and production as
 *                                 similar as possible (same image everywhere).
 *   XI.   Logs                 -- treat logs as an event STREAM to
 *                                 stdout/stderr; never manage log files
 *                                 in-app.
 *   XII.  Admin processes      -- run one-off admin/maintenance tasks
 *                                 (migrations, batch jobs) as one-off
 *                                 processes in the same environment/codebase.
 *
 * Spring Boot's defaults already lean twelve-factor: externalized
 * application.properties/environment-variable binding (III), an embedded
 * server (VII), stateless-by-convention REST controllers (VI), and
 * Actuator's /actuator/health liveness/readiness endpoints (IX) -- which is
 * a large part of why Java-on-the-cloud in practice usually means Spring
 * Boot specifically.
 * ---------------------------------------------------------------------------
 */
