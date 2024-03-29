package com.digitaldan.jomnilinkII;

public class OmniNotConnectedException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1445272024843496166L;
	private Exception exception;
	
	public OmniNotConnectedException(Exception exception) {
		this.exception = exception;
	}

	public OmniNotConnectedException(String message) {
		super(message);
	}

	public OmniNotConnectedException(Throwable cause) {
		super(cause);
	}

	public OmniNotConnectedException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public Exception getNotConnectedReason(){
		return exception;
	}
}
