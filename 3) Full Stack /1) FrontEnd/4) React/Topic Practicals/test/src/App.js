import './App.css';
import React, { useState } from 'react';


function App() {

  const [input, setInput] = useState();

  const handleChange = (e) => {
    setInput(e.target.value);
  }
  console.log(input);
  return (
    <div className="App">
      <input type="text" value={input} name='l' onChange={(e) => handleChange(e)} />
    </div>
  );
}

export default App;
