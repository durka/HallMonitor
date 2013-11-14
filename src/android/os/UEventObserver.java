package android.os;

/*
 * This is a dummy class.
 * android.os.UEventObserver is part of the hidden API
 * At runtime, the real UEventObserver will be loaded.
 */

public abstract class UEventObserver {

	public final void startObserving(String match) {
		// dummy method for a dummy class
	}
	
	public final void stopObserving() {
		// dummy method for a dummy class
	}
	
	public abstract void onUEvent(UEvent event);
	
	// dummy subclass
	public static final class UEvent {
		
	}
	
}
