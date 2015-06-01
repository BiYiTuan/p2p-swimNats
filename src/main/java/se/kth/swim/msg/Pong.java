package se.kth.swim.msg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong {

	private Map<Integer,PiggyBackElement> map;
	private UUID sn;

	public UUID getSn() {
		return sn;
	}

	public void setSn(UUID sn) {
		this.sn = sn;
	}

	public Pong(Map<Integer, PiggyBackElement> map, UUID sn) {
		super();
		this.map = map;
		this.sn = sn;
	}

	public Pong(){
		
	}
	
	public Pong(Map<Integer,PiggyBackElement> map) {
		this.map =new HashMap<Integer,PiggyBackElement>();
		this.map= map;
	}

	public Map<Integer,PiggyBackElement> getElements() {
		return map;
	}

	public void setElements(Map<Integer,PiggyBackElement> map) {
		this.map = map;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return map.toString();
	}
	
	
	
	
}
