package bgu.spl.mics;

import java.util.*;
import java.util.concurrent.*;


/**
 * The {@link MessageBusImpl class is the implementation of the MessageBus interface.
 * Write your implementation here!
 * Only one public method (in addition to getters which can be public solely for unit testing) may be added to this class
 * All other methods and members you add the class must be private.
 */
public class MessageBusImpl implements MessageBus {

	// Separate maps for Event and Broadcast subscriptions
	//map for Event. f we use round-robin indices to implement round-robin behavior, the eventSubscriptions field must be a List and not a Queue.
	//Because A List allows direct access to elements using their index and this is necessary for implementing round-robin via an external field like roundRobinIndices
	// (A Queue operates on a FIFO (First-In-First-Out) principle.)
	private final Map<Class<? extends Event>, List<MicroService>> eventSubscriptions;

	//map for Broadcast
	private final Map<Class<? extends Broadcast>, List<MicroService>> broadcastSubscriptions;

	// Mapping from each MicroService to its own message queue
	private final Map<MicroService, BlockingQueue<Message>> queues;
	//BlockingQueue is a java Queue that support operations that wait for the queue to become non-empty when retrieving and removing an element, and wait for space to become available in the queue when adding an element.

	// Mapping from events to their associated Futures
	private final Map<Event<?>, Future<?>> eventFutures;

	// Round-robin (scheduling algorithm used to distribute tasks evenly among a group of workers (or resources)) indices for distributing events among MicroService subscribers
	// Used to distribute events among the MicroServices subscribed to handle those events.
	private final Map<Class<? extends Event>, Integer> roundRobinIndices;

	// Singleton instance of the MessageBusImpl
	private static MessageBusImpl instance = null;


	private MessageBusImpl() {
		eventSubscriptions = new ConcurrentHashMap<>(); // Thread-safe mapping for Event subscriptions
		broadcastSubscriptions = new ConcurrentHashMap<>(); // Thread-safe mapping for Broadcast subscriptions
		queues = new ConcurrentHashMap<>();        // Thread-safe mapping for MicroService queues
		eventFutures = new ConcurrentHashMap<>();  // Thread-safe mapping for event Futures
		roundRobinIndices = new ConcurrentHashMap<>(); // Thread-safe mapping for round-robin indices
	}

	// Singleton instance
	private static MessageBusImpl instance_ = null;

	/**
	 * Public method to get the Singleton instance of the MessageBusImpl.
	 * Ensures thread-safe instantiation.
	 * @return the Singleton instance of MessageBusImpl.
	 */
	public static synchronized MessageBusImpl getInstance() {
		if (instance == null) {
			instance = new MessageBusImpl();
		}
		return instance;
	}


	@Override
	public <T> void subscribeEvent(Class<? extends Event<T>> type, MicroService m) {
		// Registers a Microservice to handle specific type of : EVENT
		// event is ine-to-one message (it is sent to one specifiec microservice)
		synchronized (eventSubscriptions) {
			eventSubscriptions.putIfAbsent(type, new ArrayList<>()); // This way we ensure the QUEUE exist for this EVENT type
			//The putIfAbsent() method writes an entry into the map. If an entry with the same key already exists and its value is not null then the map is not changed.
			eventSubscriptions.get(type).add(m); // Register the microservice to the subscription list
		}
	}

	@Override
	public void subscribeBroadcast(Class<? extends Broadcast> type, MicroService m) {
		// Registers a Microservice to handle specific type of : BROADCAST
		// broadcast is ine-to-many message ( it is sent to all subscribed microservices without expecting a specific response
		synchronized (broadcastSubscriptions) {
			broadcastSubscriptions.putIfAbsent(type, new ArrayList<>()); //This way we ensure the LIST exist for this BROADCAST type
			broadcastSubscriptions.get(type).add(m); // Register the microservice to the subscriptions list
		}
	}

	@Override
	public <T> void complete(Event<T> e, T result) {
		// once  theFuture associated with the event, resolve it (future)
		Future<T> future = (Future<T>) eventFutures.get(e); // Retrieve the Future associated with the Event
		if (future != null) {future.resolve(result);} // Resolve the Future with the result
	}

	@Override
	public void sendBroadcast(Broadcast b) {
		// Sends a BROADCAST msg to: ALL MicroService (instances subscribed to its type)
		synchronized (broadcastSubscriptions) {
			//The getOrDefault() method returns the value of the entry in the map which has a specified key. If the entry does not exist then the value of the second parameter is returned.
			List<MicroService> subscribers = broadcastSubscriptions.getOrDefault(b.getClass(), Collections.emptyList()); // also ensures that if no subsribers exist for the broadcast type, empty list is returned
			//Notify : because it is a broadcast it is not expecting a response
			for (MicroService m : subscribers) {
				BlockingQueue<Message> queue = queues.get(m);
				if (queue != null) {
					queue.offer(b); // Add the Broadcast to the MicroService's queue
				}
			}
		}

	}


	@Override
	public <T> Future<T> sendEvent(Event<T> e) {
		// Sends a EVENT msg to: ALL MicroService (instances subscribed to its type)
		// round-robin strategy
		synchronized (eventSubscriptions) {
			List<MicroService> subscribers = eventSubscriptions.get(e.getClass());
			if (subscribers == null || subscribers.isEmpty())
				return null; // No MicroServices are subscribed to this Event type

			// Get the round-robin index for this Event type
			int index = roundRobinIndices.getOrDefault(e.getClass(), 0);
			MicroService target = subscribers.get(index); // Select the MicroService in the current index

			// Update the round-robin index for the next Event
			roundRobinIndices.put(e.getClass(), (index + 1) % subscribers.size());
			//If EventA has 3 subscribers: Service1, Service2, and Service3, the first event goes to Service1, the second to Service2, the third to Service3, and the fourth wraps back to Service1.

			// Add the Event to the target's queue
			BlockingQueue<Message> queue = queues.get(target);
			if (queue != null) {
				queue.offer(e); // Assign the Event to the MicroService's message queue
			}

			// Create and store the Future associated with this Event
			Future<T> future = new Future<>();
			eventFutures.put(e, future);
			return future;
		}

	}

	@Override
	public void register(MicroService m) {
		// register a microservive in the sys
		// init msg queue
		queues.putIfAbsent(m, new LinkedBlockingDeque<>());
		//The putIfAbsent() method writes an entry into the map. If an entry with the same key already exists and its value is not null then the map is not changed.
	}

	@Override
	public void unregister(MicroService m) {
		// removes (unregister..) a microservice from the sys
		// cleaning up: msg queue and its subscription
		BlockingQueue<Message> queue = queues.remove(m);
		if(queue != null) { queue.clear(); } // clear all messages in the queue

		synchronized (eventSubscriptions){ for( List<MicroService> subscribers : eventSubscriptions.values()) subscribers.remove(m);}

		synchronized (broadcastSubscriptions){ for( List<MicroService> subscribers : broadcastSubscriptions.values()) subscribers.remove(m);}

		//Only if necessary, removed associated futers
		//entrySet() returns a set view of the key-value pairs in the eventFuteres map. then "removeIf(...)" iterates over the entries in the Set and removes any entry where the provided condition in the lambda
		eventFutures.entrySet().removeIf(entry -> entry.getValue().equals(m));

	}

	@Override
	public Message awaitMessage(MicroService m) throws InterruptedException {
		// Blocks until a msg is availaible for the microservice and then return the msg
		BlockingQueue<Message> queue = queues.get(m);
		if (queue == null) { throw new IllegalArgumentException("Microservice is not registerd");}
		return queue.take(); // retrieve and remove the head of this queue
	}

}
