import React, { useState } from "react";
import "bootstrap/dist/css/bootstrap.min.css";
import { PiHandWaving } from "react-icons/pi";
import { IoSunnyOutline } from "react-icons/io5";
import { FaRegStar } from "react-icons/fa";
import { CiHome } from "react-icons/ci";

export default function Sidebar() {
  const [isOpen, setIsOpen] = useState(true);

  return (
    <div className="d-flex">
      <div
        className="bg-dark vh-100 d-flex flex-column justify-content-between"
        style={{
          width: isOpen ? "175px" : "50px",
          transition: "width 0.3s ease-in-out",
        }}
      >
        <div
          className="p-3 text-white"
          onClick={() => setIsOpen(!isOpen)}
          style={{ cursor: "pointer" }}
        >
          ☰
        </div>
        <ul className="nav flex-column flex-grow-1">
          <li className="nav-item">
            <a href="/Myday" className="nav-link text-white">
              {" "}
              <IoSunnyOutline />
              {isOpen && "  My Day"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/" className="nav-link text-white">
            <FaRegStar />
              {isOpen && "   Important"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/" className="nav-link text-white">
              📊 {isOpen && "Planned"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/" className="nav-link text-white">
              📦 {isOpen && "Assigned to me"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/Task" className="nav-link text-white">
            <CiHome />

              {isOpen && "  Tasks"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/" className="nav-link text-white">
            <PiHandWaving />
            {isOpen && " Getting started"}
            </a>
          </li>
          <li className="nav-item">
            <a href="/" className="nav-link text-white">
              🛒 {isOpen && "Groceries"}
            </a>
          </li>
        </ul>
        <div className="p-3 text-white">
          <a href="/" className="nav-link text-white">
            👤 {isOpen && "Profile"}
          </a>
        </div>
      </div>
    </div>
  );
}
