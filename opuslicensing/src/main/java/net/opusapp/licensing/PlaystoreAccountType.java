package net.opusapp.licensing;

public class PlaystoreAccountType implements IAccountType {	
	private final String playstoreType = "com.google";
		
	@Override
	public String getType() {
		return playstoreType;
	}
}
