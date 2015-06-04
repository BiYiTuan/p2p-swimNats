package se.kth.swim.msg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import se.kth.swim.msg.net.NodeStatus;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong  extends FDMessage{
	
	private NatedAddress forwardNode;
	private NodeStatus alive;
	private UUID initialUUID;

	public Pong(UUID sn, Map<Integer, PiggyBackElement> nodes,NatedAddress forwardNode,UUID initialUuid) {
		super(sn, nodes);
		// TODO Auto-generated constructor stub
		this.forwardNode=forwardNode;
		this.initialUUID=initialUuid;
	}
	 
	public UUID  getinitialUUID() {
		return initialUUID;
	}

	public void setinitialUUID(UUID  initialUUID) {
		this.initialUUID = initialUUID;
	}

	public Pong(){
		
	}

	public NatedAddress getForwardNode() {
		return forwardNode;
	}

	public void setForwardNode(NatedAddress forwardNode) {
		this.forwardNode = forwardNode;
	}
	
	
	
	
	
}
