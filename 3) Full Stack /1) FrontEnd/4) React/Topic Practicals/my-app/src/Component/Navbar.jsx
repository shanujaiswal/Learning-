import React from "react";
import PropTypes from "prop-types";

export default function Navbar(props) {
  return (
    <div>
      <nav
        className={`navbar navbar-expand-lg navbar-${props.mode} bg-${props.mode}`}
      >
        <div className="container-fluid">
          <a className="navbar-brand" href="/Home">
            {props.Title}
          </a>
          <button
            className="navbar-toggler"
            type="button"
            data-bs-toggle="collapse"
            data-bs-target="#navbarSupportedContent"
            aria-controls="navbarSupportedContent"
            aria-expanded="false"
            aria-label="Toggle navigation"
          >
            <span className="navbar-toggler-icon"></span>
          </button>
          <div className="collapse navbar-collapse" id="navbarSupportedContent">
            <ul className="navbar-nav me-auto mb-2 mb-lg-0">
              <li className="nav-item">
                <a className="nav-link active" aria-current="page" href="/Text">
                  {props.Text1}
                </a>
              </li>
              <li className="nav-item">
                <a className="nav-link" href="/About">
                  {props.Text2}
                </a>
              </li>
              <li className="nav-item dropdown">
                <a
                  className="nav-link dropdown-toggle"
                  href="/"
                  id="navbarDropdown"
                  role="button"
                  data-bs-toggle="dropdown"
                  aria-expanded="false"
                >
                  {props.Text3}
                </a>
                <ul className="dropdown-menu" aria-labelledby="navbarDropdown ">
                  <li>
                    <a className="dropdown-item" href="/">
                      {props.Text4}
                    </a>
                  </li>
                  <li>
                    <a className="dropdown-item" href="/">
                      {props.Text5}
                    </a>
                  </li>
                  <li>
                    <hr className="dropdown-divider" />
                  </li>
                  <li>
                    <a className="dropdown-item" href="/">
                      {props.Text6}
                    </a>
                  </li>
                </ul>
              </li>
            </ul>
            <form className="d-flex">
              <input
                className="form-control me-2"
                type="search"
                placeholder="Search"
                aria-label="Search"
              />
              <button className="btn btn-outline-success" type="submit">
                {/* to make success- green Primary -- Blue */}
                Search
              </button>
            </form>
            <button className={"btn btn-outline-success mx-2"} type="submit">
              Red
            </button>
            <button className={"btn btn-outline-success mx-2"} type="submit">
              Blue
            </button>
            <button className={"btn btn-outline-success mx-2"} type="submit">
              Green
            </button>

            <div
              className={`form-check form-switch text-${
                props.mode === "light" ? "dark" : "light"
              } mx-2`}
            >
              <input
                className="form-check-input "
                type="checkbox"
                role="switch"
                id="flexSwitchCheckDefault"
                onClick={props.toggleMode}
              />
              <p style={{ display: "contents" }}>Enable Dark Mode</p>
            </div>
          </div>
        </div>
      </nav>
    </div>
  );
}

Navbar.propTypes = { Title: PropTypes.string, Text1: PropTypes.string };
