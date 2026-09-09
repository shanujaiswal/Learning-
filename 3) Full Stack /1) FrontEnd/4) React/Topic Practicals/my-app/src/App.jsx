import { useState } from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import "./App.css";
import About from "./Component/About";
import Navbar from "./Component/Navbar";
import TextForm from "./Component/TextForm";
import Alert from "./Component/Alert";

function AppOld() {
  const [mode, setMode] = useState("light");
  const [alert, setAlert] = useState(null);

  const showAlert = (message, type) => {
    setAlert({ msg: message, type: type });
    setTimeout(() => setAlert(null), 1000);
  };

  const toggleMode = () => {
    if (mode === "light") {
      setMode("dark");
      document.body.style.backgroundColor = "#042743";
      document.body.style.color = "white";
      showAlert("Dark mode has been enabled", "success");
      document.title = "Shanu Jaiswal - Dark Mode";
    } else {
      setMode("light");
      document.body.style.backgroundColor = "white";
      document.body.style.color = "black";
      showAlert("Light mode has been enabled", "success");
      document.title = "Shanu Jaiswal - Light Mode";
    }
  };

  return (
    <Router>
      <Navbar
        Title="Shanu Jaiswal"
        Text1="Home"
        Text2="About Us"
        Text3="Brief Introduction"
        Text4="Age"
        Text5="Location"
        Text6="SomeThing Else"
        mode={mode}
        toggleMode={toggleMode}
      />
      <Alert alert={alert} />

      <Routes>
        <Route
          path="Home"
          element={
            <>
              <div className="container my-3">
                <TextForm
                  showAlert={showAlert}
                  Heading="Enter the Text"
                  mode={mode}
                />
              </div>
              <div className="container">
                <About Heading="About Me" />
              </div>
            </>
          }
        />

        <Route
          path="/Text"
          element={
            <div className="container my-3">
              <TextForm
                showAlert={showAlert}
                Heading="Enter the Text"
                mode={mode}
              />
            </div>
          }
        />
        <Route
          path="/About"
          element={
            <div className="container">
              <About Heading="About Me" />
            </div>
          }
        />
      </Routes>
    </Router>
  );
}

export default AppOld;
