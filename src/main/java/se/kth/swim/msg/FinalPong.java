package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

public class FinalPong extends Pong{

	public FinalPong(UUID sn, Map<Integer, PiggyBackElement> nodes) {
		super(sn, nodes);
		// TODO Auto-generated constructor stub
	}

	@Override
	public UUID getSn() {
		// TODO Auto-generated method stub
		return super.getSn();
	}

	@Override
	public void setId(UUID sn) {
		// TODO Auto-generated method stub
		super.setId(sn);
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
