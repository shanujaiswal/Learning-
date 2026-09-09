# The Sense Half of the Loop

--> Sensors are how a robot finds out anything at all about itself or the world. They split cleanly into two families based on what they measure, and the distinction matters because it determines what a sensor can and can't tell the robot.

# Proprioceptive Sensors

--> **Proprioceptive sensors** measure the robot's own internal state -- joint angles, velocities, orientation -- independent of the external environment. They're the robotic equivalent of your sense of where your own limbs are with your eyes closed.

--> **Encoders** -- attached to a motor shaft or joint, they count discrete ticks as the shaft rotates, giving joint position (and, by differentiating over time, velocity). Incremental encoders count ticks relative to a start point and lose absolute position on power loss; absolute encoders report a unique position value at all times, at higher cost. Encoder resolution (ticks per revolution) directly bounds how precisely you know where a joint actually is -- which bounds how precisely forward kinematics (previous chapter) can be trusted.

--> **IMUs (Inertial Measurement Units)** -- combine an accelerometer (linear acceleration along 3 axes) and a gyroscope (angular velocity around 3 axes), often with a magnetometer (heading, like a compass). Used for orientation and balance, especially critical in mobile robots, drones, and legged robots. IMUs drift over time when you integrate their raw readings into position -- a small constant error in acceleration becomes a growing error in estimated velocity, then an even faster-growing error in estimated position. This is exactly the kind of noise problem that motivates the filtering discussed in the SLAM chapter.

# Exteroceptive Sensors

--> **Exteroceptive sensors** measure the external environment.

--> **Cameras** -- rich, dense information (color, texture, shape) but computationally expensive to interpret and sensitive to lighting. Feed directly into the perception techniques covered in 4) Data Science and AI/5) Artificial Intelligence's Computer Vision material and the broader Machine Learning/Deep Learning folders.

--> **LIDAR (Light Detection and Ranging)** -- spins a laser and measures time-of-flight to build a precise distance map (a "point cloud") of surroundings, typically 360 degrees. Excellent for mapping and obstacle avoidance, expensive relative to cameras, and struggles with some reflective or transparent surfaces.

--> **Ultrasonic sensors** -- emit a sound pulse and measure the echo's return time to estimate distance. Cheap, simple, short-range, and imprecise compared to LIDAR, but perfectly adequate for basic obstacle detection in low-cost mobile robots -- the same class of sensor covered on the microcontroller side in 7) IoT.

--> **Infrared (IR) sensors** -- measure reflected IR light for short-range proximity or use IR time-of-flight for distance; cheap and fast but affected by ambient light and surface color/reflectivity.

# The Act Half of the Loop

--> **DC motors** -- simple, cheap, fast, and high-speed, but they don't inherently know their own position (you pair them with an encoder to close a position control loop) and don't hold a fixed position under load without active control.

--> **Servo motors** -- a DC (or sometimes stepper) motor pre-packaged with a built-in encoder/potentiometer and a small controller, so you command an angle directly and it holds that angle, including under moderate load. Precision and holding torque are good for the price; speed is moderate. The default choice for small robotic arms, hobby robotics, and any joint that needs to hold a specific angle.

--> **Stepper motors** -- move in discrete, fixed-size steps per electrical pulse, giving precise open-loop positioning (you can often track position just by counting pulses sent, no encoder required) and strong holding torque at a standstill. Trade-off: lower top speed, can silently "skip steps" and lose position under excessive load or acceleration without you knowing (since there's often no feedback), and more current draw at standstill to maintain that holding torque. Common in 3D printers and CNC machines where absolute precision at moderate speed matters more than raw speed.

--> The general trade-off triangle: **DC motors** win on speed and cost, **servos** win on ease of precise positioning at moderate cost, **steppers** win on precision and holding torque without feedback hardware, at the cost of speed and the risk of silent step loss.

# Why Raw Sensor Data Is Never Trusted Directly

--> Every sensor above has noise -- small, random, or systematic errors in every reading. An encoder can have quantization error (can only report position to the nearest tick). An ultrasonic sensor can get a spurious echo off an unexpected surface. A camera frame can be motion-blurred. An IMU accumulates drift, as noted above.

--> If a control loop or planner acted on one single raw sensor reading at face value, it would jitter, overreact to noise spikes, or slowly drift off course. This is precisely the problem that **filtering** exists to solve -- techniques like moving averages, Kalman filters, and particle filters combine multiple noisy readings (and often multiple different sensors, called **sensor fusion**) into a single, more trustworthy estimate. The next chapter (Control Systems and PID Controllers) shows the simplest form of "don't trust one reading blindly" in action, and the SLAM chapter later in this folder covers Kalman and particle filters properly.

# Deep Dive: Sensor Placement Changes What "Noise" Even Means

--> It's tempting to think of sensor noise as a fixed property of the sensor itself, but placement and mounting matter just as much. An IMU mounted near a vibrating motor picks up mechanical vibration as if it were real acceleration -- the sensor is working correctly, but its signal is now dominated by a noise source that has nothing to do with sensor quality. An ultrasonic sensor angled toward a corner instead of a flat wall can get a specular reflection that makes a nearby object appear much farther away than it is. Debugging "the sensor is giving bad readings" in real robots very often turns out to be a mounting or environment problem, not a sensor defect -- always check physical placement before assuming the hardware itself is faulty.
