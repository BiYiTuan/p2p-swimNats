package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

import se.sics.p2ptoolbox.util.network.NatedAddress;


public class IndirectPong extends Pong{
	
	private NatedAddress forwardNode;
	private UUID currentWaitingId;

	public IndirectPong(UUID sn, Map<Integer, PiggyBackElement> nodes,NatedAddress forwardNode,UUID currentWaitingId) {
		super(sn, nodes);
		this.forwardNode=forwardNode;
		this.currentWaitingId=currentWaitingId;
		// TODO Auto-generated constructor stub
	}

	@Override
	public UUID getSn() {
		// TODO Auto-generated method stub
		return super.getSn();
	}

	public UUID getCurrentWaitingId() {
		return currentWaitingId;
	}

	public void setCurrentWaitingId(UUID currentWaitingId) {
		this.currentWaitingId = currentWaitingId;
	}

	@Override
	public void setId(UUID sn) {
		// TODO Auto-generated method stub
		super.setId(sn);
	}

	public NatedAddress getForwardNode() {
		return forwardNode;
	}

	public void setForwardNode(NatedAddress forwardNode) {
		this.forwardNode = forwardNode;
	}

	@Override
	public Map<Integer, PiggyBackElement> getNodes() {
		// TODO Auto-generated method stub
		return super.getNodes();
	}

	@Override
	public void setNodes(Map<Integer, PiggyBackElement> nodes) {
		// TODO Auto-generated method stub
		super.setNodes(nodes);
	}

}
