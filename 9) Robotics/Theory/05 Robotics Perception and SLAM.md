# Why Perception Is Hard

--> "Perception" means turning raw sensor data into an understanding of the world useful for decision-making -- "there is an obstacle 2 meters ahead," "that object is a coffee mug," "I am at this exact position in this room." It's one of the hardest problems in robotics for three compounding reasons.

--> **Noise.** Every sensor reading is imperfect, as covered in the Sensors and Actuators chapter -- a camera frame can be blurred or poorly lit, a LIDAR return can be a stray reflection, an IMU accumulates drift.

--> **Partial observability.** A robot never sees the whole environment at once -- a camera has a limited field of view, LIDAR can't see through walls or around corners, and objects occlude each other. The robot must act on an incomplete picture and update that picture as it moves and gathers more data.

--> **Real-time constraints.** A self-driving car can't take three seconds to decide there's a pedestrian in the crosswalk -- perception has to run fast enough to matter, on modest onboard compute, which forces trade-offs between accuracy and speed that a research paper's benchmark numbers don't have to make.

--> Object recognition and scene understanding specifically lean heavily on the techniques in 4) Data Science and AI/5) Artificial Intelligence's Computer Vision material and the Deep Learning folder -- convolutional networks and beyond, trained to extract "what is in this image" from raw pixels despite all three problems above.

# SLAM: Simultaneous Localization and Mapping

--> **SLAM** tackles a genuinely circular problem: to know *where you are*, you need a map of the environment to localize yourself against. But to *build a map*, you need to know where you are (and were, as you moved) so you can correctly place new sensor observations onto it. Neither sub-problem has a clean starting point without the other already being solved -- hence "simultaneous."

--> A mobile robot starting in a completely unknown environment (no prior map, no GPS, common indoors and in many outdoor settings too) must build the map *and* track its own position within that map at the same time, using only noisy proprioceptive sensors (encoders and IMU, subject to drift) and noisy exteroceptive sensors (camera or LIDAR) as it moves.

# How Filters Resolve the Chicken-and-Egg Problem

--> The practical trick is probabilistic estimation: rather than committing to one single "this is exactly where I am" belief, SLAM algorithms maintain a *probability distribution* over possible positions (and map states), and update that distribution incrementally as new, individually unreliable sensor data arrives.

--> **Kalman filters** maintain a single Gaussian (bell-curve) estimate of position, described by a mean and a covariance (uncertainty). Each cycle has two steps: **predict** (use the robot's motion model -- "I commanded forward at 1 m/s for 0.1 seconds, so I probably moved about 0.1m forward," which grows the uncertainty because that motion estimate itself has error) and **update** (a new sensor reading arrives, and it's blended with the prediction, weighted by how confident each source is -- confident sensor readings pull the estimate toward them and shrink uncertainty; noisy ones pull less). Kalman filters are computationally cheap and mathematically elegant, but assume the underlying uncertainty is Gaussian and the motion/sensor models are linear (or approximately so, as in the Extended Kalman Filter), which limits accuracy in highly nonlinear scenarios.

--> **Particle filters** represent the belief about position as a large swarm of discrete weighted guesses ("particles"), each an independent hypothesis of "I might be here." Each cycle: move every particle according to the motion model (with a bit of random noise added to each, spreading the swarm), then re-weight each particle by how well its hypothesized position would explain the actual new sensor reading, then resample -- keep more copies of high-weight particles, discard low-weight ones. Over many cycles the particle swarm concentrates around the true position. Particle filters handle non-Gaussian, multi-modal uncertainty naturally (useful right after a "kidnapped robot" scenario where the robot genuinely doesn't know if it's in room A or room B) at the cost of needing many particles -- and therefore more compute -- to represent the distribution well.

--> Both approaches are, at their core, doing the same thing as sensor fusion mentioned in the Sensors chapter and the same reasoning a PID controller's integral term does in miniature: never trust one noisy reading in isolation, always combine it with what you already believed, weighted by how much you trust each source.

# Path Planning: A* Basics

--> Once a usable map exists (from SLAM, or given in advance), the robot needs to find a route from its current position to a goal -- **path planning**. The map is typically discretized into a grid or graph of connected cells/nodes, some blocked by obstacles.

--> **A\* (A-star)** is the standard algorithm here: it explores the graph outward from the start, at each step expanding the node with the lowest `f(n) = g(n) + h(n)`, where `g(n)` is the actual cost accumulated to reach node n, and `h(n)` is a heuristic *estimate* of the remaining cost from n to the goal (commonly straight-line or Manhattan distance). The heuristic is what makes A* efficient compared to blind search -- it actively steers exploration toward the goal instead of expanding uniformly in all directions, while `g(n)` guarantees the path found is still optimal (shortest/cheapest), as long as the heuristic never overestimates the true remaining cost.

--> Once A* returns a sequence of waypoints, that sequence becomes the setpoint stream fed into the control loop from the PID chapter -- planning decides *where* to go next, control decides *how* to actually get the actuators to move there smoothly.

# Deep Dive: A Perfect Map Doesn't Save You From a Bad Motion Model

--> A subtle failure mode: teams sometimes assume that once SLAM produces a good map, localization is "solved," and stop worrying about the robot's motion model. But the predict step in both Kalman and particle filters depends on that motion model being reasonably accurate -- if wheel encoders overestimate distance traveled because of unmodeled wheel slip on a slick floor, the filter's predictions drift systematically in one direction between sensor updates, and if sensor updates are sparse (a LIDAR scan every second, say) the robot can wander noticeably off its believed position in the gaps between corrections, even with a perfect map sitting right there. Good SLAM in practice means keeping both halves -- the map/sensor model and the motion model -- honest, not just one.
