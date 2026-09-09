import React, { useEffect, useState } from "react";

export default function Clock() {
  const [time, setTime] = useState("00:00:00");
  // The first value, time, is our current state.
  // The second value, setTime, is the function that is used to update our state.

  useEffect(() => {
    const updateClock = () => {
      const now = new Date();

      const formattedTime = now.toLocaleTimeString("en-US", {
        hour12: false,
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
      setTime(formattedTime);
    };

    const interval = setInterval(updateClock, 1000);
    updateClock();

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="clock-container">
      <div className="time" id="time">
        {time}
      </div>
    </div>
  );
}
