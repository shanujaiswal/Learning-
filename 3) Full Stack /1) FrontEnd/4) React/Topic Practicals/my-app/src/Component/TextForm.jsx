import React, { useState } from "react";

export default function TextForm(props) {
  // state variables
  const [text, setText] = useState("");

  // functions
  const handleUpClick = () => {
    console.log("Uppercase was clicked" + text);
    let handleUpClick = text.toUpperCase();
    props.showAlert("Converted to uppercase!", "success");
    setText(handleUpClick);
  };
  const handleLoClick = () => {
    console.log("lowercase was clicked" + text);
    let handleLoClick = text.toLowerCase();
    props.showAlert("Converted to lowercase!", "success");
    setText(handleLoClick);
  };
  const handleClearClick = () => {
    console.log("Text is Cleared on clicking");
    let handleClearClick = "";
    props.showAlert("Text is Clear !", "success");
    setText(handleClearClick);
  };
  const handleSentenceCase = () => {
    let handleSentenceCase =
      text.charAt(0).toUpperCase() + text.substring(1).toLowerCase();
    props.showAlert("Converted to Sentancecase!", "success");
    setText(handleSentenceCase);
  };
  // const handleCopy = () => {
  //   let handleCopy = navigator.clipboard.writeText(text);
  //   setText(handleCopy);
  // };

  //To be read properly
  const handleCamelCase = () => {
    let words = text
      .toLowerCase()
      .split(" ")
      .filter((word) => word !== ""); // Remove extra spaces

    let newText =
      words[0] +
      "" +
      words
        .slice(1)
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join("");

    setText(newText);
  };

  const handleOnChange = (event) => {
    setText(event.target.value);
  };

  // HTML
  return (
    <>
      <div>
        <h1>{props.Heading}</h1>
        <div className="mb-3">
          <textarea
            className="form-control"
            value={text}
            onChange={handleOnChange}
            id="myBox"
            rows="8"
            placeholder="Enter Text here"
          ></textarea>
        </div>
        <button className="btn btn-primary m-1 " onClick={handleUpClick}>
          Convert To UpperCase
        </button>
        <button className="btn btn-primary   m-1" onClick={handleLoClick}>
          Convert To lowerCase
        </button>
        <button className="btn btn-primary   m-1" onClick={handleClearClick}>
          Clear The Text
        </button>
        <button className="btn btn-primary    m-1" onClick={handleSentenceCase}>
          Convert To Sentence Case
        </button>

        {/* <button className="btn btn-primary    m-1" onClick={handleCopy}>
          Copy The Text
        </button> */}

        <button className="btn btn-primary    m-1" onClick={handleCamelCase}>
          Convert To Camel Case
        </button>
      </div>
      <div className="container  my-3">
        <h1>Your text summary</h1>
        <p>
          {text.split(" ").length -1 } words and {text.length} Characters
        </p>
        <p>{0.008 * text.split(" ").length} Minutes read</p>
        <h2>Preview</h2>
        <p>
          {text.length > 0
            ? text
            : "Enter something in the text above to preview "}
        </p>
      </div>
    </>
  );
}
