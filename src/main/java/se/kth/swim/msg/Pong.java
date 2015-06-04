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

	public Pong(UUID sn, Map<Integer, PiggyBackElement> nodes,NatedAddress forwardNode,NodeStatus alive) {
		super(sn, nodes);
		// TODO Auto-generated constructor stub
		this.forwardNode=forwardNode;
		this.alive=alive;
	}
	 
	public NodeStatus getAlive() {
		return alive;
	}

	public void setAlive(NodeStatus alive) {
		this.alive = alive;
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
