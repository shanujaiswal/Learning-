import React from "react";
import Clock from "./Clock";
import Task from "./Task";
import { LuExternalLink } from "react-icons/lu";

export default function My_Day() {
  return (
    <>
      <div>
        <h1>My Day</h1>
        <LuExternalLink />
      </div>
      <Clock />
      <Task />
    </>
  );
}
