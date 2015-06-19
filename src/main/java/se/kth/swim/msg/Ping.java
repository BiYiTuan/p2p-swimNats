/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class Ping {
	
	private Map<Integer,PiggyBackElement> nodes;
	private UUID sn;

	private NatedAddress forwardNode;
	private UUID initialUUID;

	public Ping(UUID sn, Map<Integer, PiggyBackElement> nodes,NatedAddress forwardNode,UUID initialUUID) {
		this.nodes=nodes;
		this.forwardNode=forwardNode;
		this.initialUUID=initialUUID;
		this.sn=sn;
		// TODO Auto-generated constructor stub
	}
	
	

	public Ping() {
		super();
	}



	public Map<Integer, PiggyBackElement> getNodes() {
		return this.nodes;
	}



	public void setNodes(Map<Integer, PiggyBackElement> nodes) {
		this.nodes = nodes;
	}



	public UUID getSn() {
		return sn;
	}



	public void setSn(UUID sn) {
		this.sn = sn;
	}



	public UUID getInitialUUID() {
		return initialUUID;
	}

	public void setInitialUUID(UUID initialUUID) {
		this.initialUUID = initialUUID;
	}

	public NatedAddress getForwardNode() {
		return forwardNode;
	}

	public void setForwardNode(NatedAddress forwardNode) {
		this.forwardNode = forwardNode;
	}



	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return this.nodes.toString()+this.nodes.keySet().toString();
	}
	
}
