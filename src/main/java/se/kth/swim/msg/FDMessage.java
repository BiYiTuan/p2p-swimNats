package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

public class FDMessage {

	private UUID sn;
	private Map<Integer, PiggyBackElement> nodes;
	
	public FDMessage(){
		
	}

	public FDMessage(UUID sn, Map<Integer, PiggyBackElement> nodes) {
		super();
		this.sn = sn;
		this.nodes = nodes;
	}

	public UUID getSn() {
		return sn;
	}

	public void setId(UUID sn) {
		this.sn = sn;
	}

	public Map<Integer, PiggyBackElement> getNodes() {
		return nodes;
	}

	public void setNodes(Map<Integer, PiggyBackElement> nodes) {
		this.nodes = nodes;
	}

}
