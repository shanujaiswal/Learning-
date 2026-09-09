# Class Components

--> Before Hooks (pre-16.8), class components were the only way to have state and lifecycle logic in React.
--> class Welcome extends React.Component { render() { return <h1>Hello {this.props.name}</h1> } }
--> Must extend React.Component and define a render() method that returns JSX.
--> State is initialized in the constructor: this.state = { count: 0 }, and read via this.state.count.
--> State is updated with this.setState({ count: this.state.count + 1 }) -- never mutate this.state directly, same rule as useState.
--> Methods often need to be bound to this (this.handleClick = this.handleClick.bind(this) in the constructor) or written as arrow function class properties to avoid losing their this context in event handlers.

# The Lifecycle (Mount → Update → Unmount)

--> Every class component goes through three phases, each with lifecycle methods that hooks were designed to replace.

# Mounting (component is created and inserted into the DOM)

--> constructor(props) --> Runs first, used to initialize state and bind methods.
--> static getDerivedStateFromProps(props, state) --> Runs right before render, on every render (mount and update) -- returns an object to update state from incoming props, or null to change nothing. Rarely needed; used when state depends on prop changes over time.
--> render() --> Returns the JSX to display.
--> componentDidMount() --> Runs once, right after the component is first rendered to the DOM -- equivalent to useEffect(() => {...}, []).
--> Common uses: fetching initial data, setting up subscriptions/timers, direct DOM measurements.

# Updating (component re-renders due to new props/state)

--> shouldComponentUpdate(nextProps, nextState) --> Lets a class component skip re-rendering by returning false, for performance optimization -- the class-component equivalent of what React.memo does for function components.
--> extends React.PureComponent instead of React.Component --> automatically implements shouldComponentUpdate with a shallow prop/state comparison, so you don't have to write it by hand.
--> render() --> Runs again whenever props or state change.
--> getSnapshotBeforeUpdate(prevProps, prevState) --> Runs right before the DOM is updated, returns a value (e.g. scroll position) that is passed as the third argument to componentDidUpdate -- used to capture info from the DOM before it changes.
--> componentDidUpdate(prevProps, prevState) --> Runs after every re-render (except the first) -- equivalent to useEffect(() => {...}, [deps]).
--> Must guard against infinite loops by comparing prevProps/prevState before calling setState inside it.

# Unmounting (component is removed from the DOM)

--> componentWillUnmount() --> Runs right before the component is removed -- equivalent to the cleanup (return) function inside useEffect.
--> Used to clear timers, cancel network requests, and remove event listeners/subscriptions to prevent memory leaks.

# Class Components vs Hooks

--> useState + useEffect(fn, []) together cover constructor + componentDidMount.
--> useEffect(fn, [deps]) covers componentDidUpdate for those specific dependencies.
--> useEffect's cleanup return function covers componentWillUnmount.
--> Function components with Hooks are now the recommended default -- class components/lifecycle methods mainly show up in legacy code and Error Boundaries (which still require a class, see Advanced React Patterns).
