/**
 * 05-streams-and-eventemitter.js
 *
 * Covers: 02 Node.js Async Patterns Streams and EventEmitter
 *
 * Two real demos in one file:
 *   1) A custom class extending EventEmitter, emitting lifecycle events.
 *   2) A real Readable -> Transform -> Writable stream pipeline that reads
 *      a text file, uppercases each line, and writes the result to a new file.
 *
 * Run: node 05-streams-and-eventemitter.js
 * Output: creates ./data/input.txt (if missing) and ./data/output.txt
 */

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { EventEmitter } = require('events');
const { Transform } = require('stream');

// ---------------------------------------------------------------------------
// 1) Custom EventEmitter-based class
// ---------------------------------------------------------------------------
class OrderProcessor extends EventEmitter {
  place(order) {
    this.emit('placed', order);

    // Simulate async processing (e.g. payment + fulfillment)
    setTimeout(() => {
      if (order.amount <= 0) {
        this.emit('error', new Error(`Invalid order amount: ${order.amount}`));
        return;
      }
      this.emit('processed', { ...order, status: 'confirmed' });
    }, 100);
  }
}

function runEventEmitterDemo() {
  const processor = new OrderProcessor();

  processor.on('placed', (order) => {
    console.log(`[EventEmitter] Order placed: #${order.id} for $${order.amount}`);
  });

  processor.on('processed', (order) => {
    console.log(`[EventEmitter] Order processed: #${order.id} -> ${order.status}`);
  });

  processor.on('error', (err) => {
    console.error(`[EventEmitter] Order failed: ${err.message}`);
  });

  processor.place({ id: 1, amount: 49.99 });
  processor.place({ id: 2, amount: -10 }); // triggers the 'error' event
}

// ---------------------------------------------------------------------------
// 2) Real stream pipeline: read a file line by line, transform, write out.
// ---------------------------------------------------------------------------
function runStreamDemo() {
  const dataDir = path.join(__dirname, 'data');
  const inputPath = path.join(dataDir, 'input.txt');
  const outputPath = path.join(dataDir, 'output.txt');

  if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir);
  if (!fs.existsSync(inputPath)) {
    fs.writeFileSync(
      inputPath,
      ['hello streams', 'node.js makes this easy', 'line by line transformation'].join('\n')
    );
  }

  const readStream = fs.createReadStream(inputPath, { encoding: 'utf8' });
  const writeStream = fs.createWriteStream(outputPath);

  // A Transform stream that uppercases each line it receives.
  const uppercaseTransform = new Transform({
    transform(chunk, encoding, callback) {
      this.push(chunk.toString().toUpperCase());
      callback();
    },
  });

  // readline lets us process the file line-by-line, but the actual data
  // movement (read file -> transform -> write file) uses real Node streams
  // via .pipe(), which handles backpressure automatically.
  readStream
    .pipe(uppercaseTransform)
    .pipe(writeStream);

  writeStream.on('finish', () => {
    console.log(`[Streams] Wrote uppercased content to ${outputPath}`);
    console.log('[Streams] Output contents:');
    console.log(fs.readFileSync(outputPath, 'utf8'));
  });

  readStream.on('error', (err) => console.error('[Streams] Read error:', err.message));
  writeStream.on('error', (err) => console.error('[Streams] Write error:', err.message));

  // Bonus: demonstrate readline for genuine line-by-line iteration (separate pass).
  const rl = readline.createInterface({ input: fs.createReadStream(inputPath) });
  let lineNumber = 0;
  rl.on('line', (line) => {
    lineNumber += 1;
    console.log(`[Streams] readline saw line ${lineNumber}: "${line}"`);
  });
}

if (require.main === module) {
  runEventEmitterDemo();
  runStreamDemo();
}

module.exports = { OrderProcessor, runEventEmitterDemo, runStreamDemo };
