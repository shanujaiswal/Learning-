# Why Message Queues Exist

--> Direct synchronous calls between services (Service A calls Service B's API and waits for a response) tightly couples them -- if B is slow or temporarily down, A is blocked or fails too. Message queues decouple producers from consumers -- a producer drops a message and moves on immediately; a consumer processes it whenever it's ready, independently.
--> Also smooths out traffic spikes -- messages queue up during a burst instead of overwhelming a downstream service all at once.

# Core Concepts

--> Producer -- creates and sends messages. Consumer -- receives and processes them. Queue/Topic -- where messages wait between production and consumption.
--> Point-to-point -- one message is consumed by exactly ONE consumer (even if several are listening, competing for work) -- good for distributing a workload across multiple workers.
--> Publish/Subscribe -- one message is delivered to EVERY subscriber -- good for broadcasting an event to multiple independent interested services.

# RabbitMQ -- Traditional Message Broker

--> RabbitMQ implements AMQP (Advanced Message Queuing Protocol) -- messages are routed through Exchanges to Queues based on flexible routing rules, giving fine-grained control over how a message gets delivered.

```python
import pika

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
channel = connection.channel()
channel.queue_declare(queue="order_processing")

# Producer
channel.basic_publish(exchange="", routing_key="order_processing", body="Order #123")

# Consumer
def callback(ch, method, properties, body):
    print(f"Processing: {body}")
    ch.basic_ack(delivery_tag=method.delivery_tag)   # Confirm successful processing

channel.basic_consume(queue="order_processing", on_message_callback=callback)
channel.start_consuming()
```

--> `basic_ack` -- explicit acknowledgment that a message was successfully processed -- if a consumer crashes before acking, RabbitMQ redelivers the message to another consumer, a core reliability guarantee.
--> Well-suited for: task queues, RPC-style request/reply patterns, and scenarios needing complex routing logic (route by message type/priority to different queues).

# Kafka -- High-Throughput Event Streaming

--> Kafka is built around an append-only, persistent log of events (Topics), retained for a configurable period (not deleted immediately after consumption like a traditional queue) -- multiple independent consumer groups can read the SAME topic at their own pace, each tracking their own position ("offset") in the log.
--> Designed for very high throughput (millions of events/second) and durability -- often the backbone of event-driven architectures, real-time analytics pipelines, and log aggregation, not just simple task queues.

```python
from kafka import KafkaProducer, KafkaConsumer

producer = KafkaProducer(bootstrap_servers="localhost:9092")
producer.send("user_events", b"user_signed_up:12345")

consumer = KafkaConsumer("user_events", bootstrap_servers="localhost:9092", group_id="analytics_service")
for message in consumer:
    print(message.value)
```

--> Because events are RETAINED (not deleted on consumption), a NEW consumer group can start reading from the BEGINNING of the topic's history whenever it comes online -- powerful for replaying past events into a newly added service, something a traditional queue (where a message disappears once consumed) can't do.

# RabbitMQ vs Kafka -- When to Use Which

--> RabbitMQ -- better fit for traditional task-queue workloads, complex routing needs, and lower absolute throughput requirements where message-level acknowledgment/retry semantics matter most.
--> Kafka -- better fit for high-volume event streaming, when multiple independent services need to consume the same event stream, or when replaying historical events matters.

# Celery -- Task Queues for Python Applications

--> Celery is a Python-specific distributed task queue, typically using Redis or RabbitMQ as its underlying message broker -- the standard way to run background/async jobs (sending emails, processing uploads, generating reports) OUTSIDE the request-response cycle of a Flask/Django/FastAPI app.

```python
from celery import Celery

app = Celery("tasks", broker="redis://localhost:6379/0")

@app.task
def send_welcome_email(user_email):
    # Runs in a separate worker process, not blocking the web request
    email_service.send(user_email, "Welcome!")
```

```python
# In a Flask/Django view -- returns immediately, doesn't wait for the email to actually send
send_welcome_email.delay("newuser@example.com")
```

--> `.delay()` -- queues the task for a Celery worker process to pick up asynchronously, instead of running it synchronously inline and making the HTTP request wait for a slow operation (like sending an email) to finish.
--> Celery Beat -- Celery's companion for SCHEDULED tasks (run this job every night at 2 AM), the Python-ecosystem equivalent conceptually to the Kubernetes CronJob covered in the DevOps folder.
