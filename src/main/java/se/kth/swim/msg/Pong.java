package se.kth.swim.msg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong {

	Map<Integer,PiggyBackElement> map;

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
