import React, { useState } from "react";

export default function Task() {
  //   const [text, setText] = useState("");
  const [placeholder, setPlaceholder] = useState("Add a task");
  const [data, setData] = useState("");
  const [error, setError] = useState("");

  const handleOnClick = () => {
    setPlaceholder("Start typing...");
  };

  const handleFetch = () => {
    fetch("http://localhost:3000/todo")
      .then((res) => {
        if (!res.ok) {
          throw new Error("Could not fetch data from resource");
        }
        return res.json();
      })
      .then((jsonData) => {
        setData(jsonData);
      })
      .catch((err) => {
        setError(err.message);
      });
  };

  return (
    <div>
      <div>
        <button onClick={handleFetch}>Click me</button>
      </div>
      {data.map((item, index) =>  (
          <>
            <p>{item.id}</p>
            <p>{item.title}</p>
            <p>{item.status}</p>
          </>
        ))}
    </div>
  );
}
