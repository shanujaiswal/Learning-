# Degrees of Freedom

--> A robot's **degrees of freedom (DOF)** is the number of independent parameters needed to fully describe its configuration. A point moving freely in 3D space has 3 DOF (x, y, z). A rigid body moving freely in 3D space has 6 DOF (3 translation + 3 rotation: roll, pitch, yaw). A robotic arm's DOF is simply the number of independently controllable joints it has.

--> More DOF means more flexibility in the poses the robot can reach, but also more joints to coordinate, more sensors to read, and a harder control and planning problem. A typical industrial arm used for welding or pick-and-place has 6 DOF -- exactly enough to place its end-effector at any position and orientation in 3D space (3 for position, 3 for orientation). Humanoid robots can have 30+ DOF once you count both arms, both legs, the torso, and the head.

# Links and Joints

--> A robot's mechanical structure is a chain of rigid **links** connected by **joints**. The joint is where motion happens; the link is the rigid segment between two joints that doesn't deform (in the idealized model -- real links do flex slightly under load, which is its own advanced topic).

--> Two joint types cover the overwhelming majority of robots:

--> **Revolute joint** -- rotates around an axis, like an elbow or a door hinge. Parameterized by an angle theta. Most robotic arms are built almost entirely from revolute joints because rotary motors are cheap, reliable, and easy to seal against dust and moisture.

--> **Prismatic joint** -- slides linearly along an axis, like a drawer or a piston. Parameterized by a displacement d. Common in gantry-style CNC machines and some industrial pick-and-place systems where straight-line motion is more natural than rotation.

--> A robot's **kinematic chain** is the ordered sequence of links and joints from a fixed base to the **end-effector** -- the gripper, tool, or hand at the tip that actually does the work. Every joint in the chain contributes one DOF (for a revolute or prismatic joint).

# Forward Kinematics

--> **Forward kinematics (FK)** answers: "given the current angle/displacement of every joint, where is the end-effector, and what orientation is it in?" This is the easy direction -- you walk down the kinematic chain, and at each joint you know the geometric transformation (rotation or translation) it applies. Chain enough transformations together (using homogeneous transformation matrices, which is exactly why 4) Data Science and AI/2) Data Science/Theory/06 Linear Algebra for Data Science and AI.md matters here -- kinematics is matrix multiplication over 4x4 transformation matrices) and you get the end-effector's position and orientation in one deterministic pass.

--> FK is always well-defined: one set of joint angles produces exactly one end-effector pose. There's no ambiguity.

# Inverse Kinematics

--> **Inverse kinematics (IK)** answers the opposite, and much harder, question: "given a desired end-effector position and orientation, what joint angles produce it?" This is what you actually need in practice -- you know where you want the gripper to end up (over a part on a conveyor belt, say); you need to solve backward for the motor commands.

--> IK is hard for three structural reasons:

--> **Multiple solutions.** A 2-link arm reaching for a point in front of it can often bend its elbow "up" or "down" and reach the same point -- both are valid, and a real controller has to pick one (often by preferring the solution closest to the arm's current configuration, to avoid a sudden violent motion).

--> **No closed-form solution for many geometries.** Below 6 DOF non-redundant arms with simple geometry, you can often derive IK algebraically with trigonometry (see the worked example below). Beyond that, especially with 7+ DOF "redundant" arms, there is no clean formula -- solutions are found numerically, iteratively nudging joint angles to reduce end-effector error (e.g. via the Jacobian pseudo-inverse or gradient-descent-style methods, which resemble optimization techniques from the Machine Learning folder more than they resemble simple algebra).

--> **Unreachable targets.** The desired pose might simply be outside the arm's reachable workspace (too far, or an orientation the joint limits can't produce), in which case IK correctly reports no solution -- the controller must fall back to reaching for the nearest feasible pose.

# Worked Example: 2-Link Planar Arm Forward Kinematics

--> Consider a robot arm confined to a 2D plane with two revolute joints and two links of length L1 and L2. Joint angles theta1 (shoulder) and theta2 (elbow, measured relative to the first link) fully describe the configuration.

```
        (x, y)  <- end-effector
           /
          / L2
         /
    elbow
        \
         \ L1
          \
        (0,0) <- base, angle theta1 from x-axis
```

--> The forward kinematics equations, derived directly from trigonometry (each link contributes its own sine/cosine offset, and the second link's angle is relative to the first, so it's added on):

```python
import math

def forward_kinematics(theta1_deg, theta2_deg, L1, L2):
    """Compute end-effector (x, y) for a 2-link planar arm."""
    theta1 = math.radians(theta1_deg)
    theta2 = math.radians(theta2_deg)

    # Position of the elbow joint
    elbow_x = L1 * math.cos(theta1)
    elbow_y = L1 * math.sin(theta1)

    # Position of the end-effector: elbow position + second link's contribution
    x = elbow_x + L2 * math.cos(theta1 + theta2)
    y = elbow_y + L2 * math.sin(theta1 + theta2)

    return x, y

# Example: shoulder at 30 degrees, elbow bent 45 degrees, links of 10cm and 8cm
x, y = forward_kinematics(30, 45, 10, 8)
print(f"End-effector position: ({x:.2f}, {y:.2f})")
```

--> Solving this same arm for IK given a target (x, y) requires the law of cosines to find theta2 from the triangle formed by L1, L2, and the distance to the target, then back-substituting to find theta1 -- and it naturally produces two valid theta2 solutions (elbow-up and elbow-down), which is the multiple-solutions problem in its simplest possible form.

# Deep Dive: Singularities

--> A **kinematic singularity** is a configuration where the arm loses a degree of freedom in practice, even though it has the DOF on paper. The classic example: fully extending a 2-link arm (theta2 = 0, arm perfectly straight) puts the end-effector at its maximum reach. At that exact configuration, an infinitesimal desired motion straight outward (further than max reach) requires an infinite joint velocity to achieve -- the Jacobian matrix that relates joint velocities to end-effector velocity becomes non-invertible there. Real controllers must detect proximity to singularities and slow down or reroute around them, because commanding motion straight through one produces wild, dangerous joint accelerations. This is one of the most common causes of "the arm suddenly jerked" bugs in real robotic systems, and it's a purely geometric problem, not a software bug in the usual sense.
