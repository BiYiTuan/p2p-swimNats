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
package se.kth.swim;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.FinalPong;
import se.kth.swim.msg.IndirectPong;
import se.kth.swim.msg.PiggyBackElement;
import se.kth.swim.msg.Ping;
import se.kth.swim.msg.PingReq;
import se.kth.swim.msg.Pong;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.IndirectPing;
import se.kth.swim.msg.net.NatPort;
import se.kth.swim.msg.net.NetFinalPong;
import se.kth.swim.msg.net.NetIndirectPing;
import se.kth.swim.msg.net.NetIndirectPong;
import se.kth.swim.msg.net.NetNatRequest;
import se.kth.swim.msg.net.NetNatResponse;
import se.kth.swim.msg.net.NetNatUpdate;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPingReq;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.NodeStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<NatPort> nat = requires(NatPort.class);
    
    private NatedAddress selfAddress;
    private final Set<NatedAddress> neighbors;
    private final NatedAddress aggregatorAddress;
    private final Map<Integer,PiggyBackElement> aliveNodes;
    private final Map<Integer,PiggyBackElement> failedNodes;
    private final Map<Integer,PiggyBackElement> suspectedNodes;
    private final Map<Integer,PiggyBackElement> piggybacked;

    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private UUID ackTimeoutId;
    private UUID suspectTimeoutId;
    private int receivedPings = 0;
    
    //number of k nodes to send indirect ping
    private final int k=3;
    //implementation of the round robin protocol
    private  int robin =0;
    
    private Map<UUID,NatedAddress> ackids = new HashMap<UUID,NatedAddress>();
    private Map<UUID,NatedAddress> suspectids = new HashMap<UUID,NatedAddress>();
    
    Double lamdalogn;

    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        
        this.ackids= new HashMap<UUID,NatedAddress>();
        this.suspectids= new HashMap<UUID,NatedAddress>();
        
        this.neighbors = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;
        this.aliveNodes=new HashMap<Integer,PiggyBackElement>();
        this.failedNodes=new HashMap<Integer,PiggyBackElement>();
        this.suspectedNodes=new HashMap<Integer,PiggyBackElement>();
        this.piggybacked=new HashMap<Integer,PiggyBackElement>();

        for (NatedAddress address: neighbors){
        	this.aliveNodes.put(address.getId(),new PiggyBackElement(address,NodeStatus.ALIVE,0));
        	
        }
        neighbors.add(selfAddress);
        aliveNodes.put(selfAddress.getId(), new PiggyBackElement(selfAddress, NodeStatus.NEW));
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleNetPingReq, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
        subscribe(handleSuspectedTimeout,timer);
        subscribe(handleNetIndirectPing,network);
        subscribe(handleNetIndirectPong,network);
        subscribe(handleNetFinalPong,network);
        subscribe(handleNetNatRequest, nat);
		subscribe(handleNetNatUpdate, nat);

    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (!neighbors.isEmpty()) {
            	log.info("{} starting...", new Object[]{selfAddress.getId()});
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            //cancel all timeouts
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
            for (UUID ackId: ackids.keySet()){
            	cancelWaitingAck(ackId);
            }
            for (UUID suspectId: suspectids.keySet()){
            	cancelSuspected(suspectId);
            }
        }
    };

	private Handler<NetPing> handlePing = new Handler<NetPing>() {

		@Override
		public void handle(NetPing event) {
			log.info("{} received ping from:{}",
					new Object[] { selfAddress.getId(),
							event.getHeader().getSource() });
			receivedPings++;
			NatedAddress source = event.getHeader().getSource();
			checkSource(source);
			if (!event.getContent().getNodes().isEmpty()) {
				// merge lists
				mergeViews(event.getContent().getNodes());
			}
			preparePiggyBackList();
			trigger(new NetPong(selfAddress, source, new Pong(event
					.getContent().getSn(), piggybacked)), network);
		}

	};
    
	private Handler<NetPong> handlePong = new Handler<NetPong>() {

		@Override
		public void handle(NetPong event) {
			// TODO Auto-generated method stub
			Pong content = event.getContent();
			if (ackids.containsKey(content.getSn())) {
				log.info("{} received piggybacked pong {} from {}",
						new Object[] { selfAddress.getId(), content.toString(),
								event.getSource().getId() });
				if (content.getNodes() != null) {
					mergeViews(content.getNodes());
				}
				checkSource(event.getSource());
				// received indirect pong from alive node
				cancelWaitingAck(content.getSn());
				// if received pong, then forward to initial node
			}
		}

	};
	
	private Handler<NetFinalPong> handleNetFinalPong = new Handler<NetFinalPong>(){

		@Override
		public void handle(NetFinalPong event) {
			// TODO Auto-generated method stub
			//if (event.getContent().getForwardNode()==null && event.getContent().getSn()==null){
				//this is the final pong
			FinalPong content = event.getContent();
				log.info("{} received piggybacked pong {} from {}",
						new Object[] { selfAddress.getId(),
						content.toString(),event.getSource().getId() });
				if (content.getNodes() != null) {
					mergeViews(content.getNodes());
				}
				checkSource(event.getSource());
			cancelSuspected(event.getContent().getSn());
		}
		
	};

	
    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {
    	//end of the periodic ping, now the systems chooses a node to be pinged
        @Override
        public void handle(PingTimeout event) {
        	//select random peer for bootstrap node
        	//should it be alive nodes or suspected
			PiggyBackElement value = selectRoundRobinNode(null);
			if (value!=null && (!ackids.containsValue(value.getAddress()))){
				preparePiggyBackList();
				log.info("{} sending periodic ping to partner:{}", new Object[] {
						selfAddress.getId(), value.getAddress() });
				scheduleWaitingAck(value.getAddress());				
				//NULL as a parameter on Ping - because it is a direct ping and not an indirect ping
				trigger(new NetPing(selfAddress, value.getAddress(), new Ping(ackTimeoutId,piggybacked, null,null)), network);
			}else if (value!=null){
				log.info("{} will not send periodic ping to partner:{} - already wait for an ack", new Object[] {
						selfAddress.getId(), value.getAddress() });
			}else if (value==null){
				//if i have no nodes to ping, cancel periodic ping
				cancelPeriodicPing();
				log.info("{} has no peers to ping - aliveNodes: {}, suspectedNodes : {}, failedNodes: {}",new Object[]{selfAddress.getId(),aliveNodes.size(),suspectedNodes.size(),failedNodes.size()});
			}
        }

    };
    
    
    

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {

        	log.info("{} sending status to aggregator:{} alive {} suspected {} failed {} total {}", new Object[]{selfAddress.getId(), aggregatorAddress,aliveNodes.size(),suspectedNodes.size(), failedNodes.size(),neighbors.size()});
//            for (Integer key: aliveNodes.keySet()){
//            	log.info("Alive Key:"+key);
//            }
//            for (Integer key: failedNodes.keySet()){
//            	log.info("Failed Key:"+key);
//            }
        	trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings,aliveNodes.size(),suspectedNodes.size(),failedNodes.size())), network);
        }

    };
    
    private Handler<AckTimeout> handleAckTimeout = new Handler<AckTimeout>(){
    	//we didn 't receive a pong message reply
		@Override
		public void handle(AckTimeout event) {
			// TODO Auto-generated method stub
			NatedAddress noReplyNode = ackids.get(event.getTimeoutId());
			log.info("{} timeout -Node {} SUSPECTED! UUID: {} Will try indirect-ping!", new Object[]{selfAddress.getId(), noReplyNode,event.getTimeoutId()});
			//implement indirect probing
			cancelWaitingAck(event.getTimeoutId());
			//if it is already suspected then there is no need for an extra indirect ping
			if (aliveNodes.containsKey(noReplyNode.getId())){
				PiggyBackElement e = aliveNodes.get(noReplyNode.getId());
				aliveNodes.remove(noReplyNode.getId());
				e.setStatus(NodeStatus.SUSPECTED);
				e.initDiseminateTimes();
				suspectedNodes.put(noReplyNode.getId(), e);
				//select k members at random
				preparePiggyBackList();
				for (int i=0; i<k; i++){
					PiggyBackElement value = selectRoundRobinNode(noReplyNode);
					//send ping-req message to peers
					trigger(new NetPingReq(selfAddress, value.getAddress(), new PingReq(scheduleWaitingSuspected(noReplyNode),piggybacked,noReplyNode)), network);
				}
				//totally new timeout
			}
			//schedule timeout for the node to be considered failed
			//but only we do not already have an indirect ping running
			//if (!ids.containsKey(event.getTimeoutId())){
								

		}
    	
    };
    
    
    private Handler<NetPingReq> handleNetPingReq = new Handler<NetPingReq>(){
    	//request to indirect ping a node
		@Override
		public void handle(NetPingReq event) {
			// TODO Auto-generated method stub
			//receiving ping-req, should indirect ping node
			checkSource(event.getSource());
			if (!event.getContent().getNodes().isEmpty()){
				//merge
				mergeViews(event.getContent().getNodes());
			}
			log.info("{} indirect pings node {}, as requested by node {}",new Object[]{selfAddress.getId(),event.getContent().getNodeToBePinged(),event.getSource()});
			preparePiggyBackList();
			//send indirect ping to node
			//indirect ping also has a normal timeout;
			trigger(new NetIndirectPing(selfAddress,event.getContent().getNodeToBePinged(), new IndirectPing(scheduleWaitingAck(event.getContent().getNodeToBePinged()), piggybacked, event.getSource(),event.getContent().getSn())), network);
		}
    	
    };
    
    private Handler<NetIndirectPing> handleNetIndirectPing = new Handler<NetIndirectPing>(){

		@Override
		public void handle(NetIndirectPing event) {
			// TODO Auto-generated method stub
        	log.info("{} received  indirect ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            NatedAddress source  = event.getHeader().getSource();
        	checkSource(source);
            receivedPings++;
            if (!event.getContent().getNodes().isEmpty()){
            	//merge lists
            	mergeViews(event.getContent().getNodes());
            }
            preparePiggyBackList();
            //event content sn is the initial -initial is the current
             trigger(new NetIndirectPong(selfAddress,source,new IndirectPong(event.getContent().getSn(),piggybacked,event.getContent().getForwardNode(),event.getContent().getInitialUUID())),network);   
		}
    	
    };
    
    private Handler<NetIndirectPong> handleNetIndirectPong = new Handler<NetIndirectPong>(){

		@Override
		public void handle(NetIndirectPong event) {
			// TODO Auto-generated method stub
			IndirectPong content = event.getContent();
			if (ackids.containsKey(content.getSn())){
				log.info("{} received piggybacked pong {} from {}",
						new Object[] { selfAddress.getId(),
						content.toString(),event.getSource().getId() });
				if (content.getNodes() != null) {
					mergeViews(content.getNodes());
				}
				checkSource(event.getSource());
				//received indirect pong from alive node
				cancelWaitingAck(content.getSn());
				//if received pong, then forward to initial node
				preparePiggyBackList();
				trigger(new NetFinalPong(selfAddress,content.getForwardNode(),new FinalPong(event.getContent().getCurrentWaitingId(), piggybacked )),network);
		}
		}
    };
    
    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>(){

		@Override
		public void handle(SuspectedTimeout event) {
			// TODO Auto-generated method stub
			log.info("{} suspecting timeout - no reply from: {} NODE FAILED!", new Object[]{selfAddress.getId(), suspectids.get(event.getTimeoutId())});
//			log.info("{} CUSTOM UUID IS: {}", new Object[]{selfAddress.getId(),event.getCustomID()});
//			for (UUID id: ids.keySet()){
//				System.out.println(id.toString());
//			}
			if (suspectids.containsKey(event.getTimeoutId())){
				Integer addressId = suspectids.get(event.getTimeoutId()).getId();
//			if (aliveNodes.containsKey(addressId)){
//				PiggyBackElement element = (PiggyBackElement) aliveNodes.get(addressId);
//				element.initDiseminateTimes();
//				element.setStatus(NodeStatus.FAILED);
//				aliveNodes.remove(addressId);
//				failedNodes.put(addressId, element);
//			}
			if (suspectedNodes.containsKey(addressId)){
				PiggyBackElement element = (PiggyBackElement) suspectedNodes.get(addressId);
				element.initDiseminateTimes();
				element.setStatus(NodeStatus.FAILED);
				suspectedNodes.remove(addressId);
				failedNodes.put(addressId, element);
			}
			}
			//ids.remove(event.getCustomID());
			cancelSuspected(event.getTimeoutId());
			//scheduleWaitingFailed(event.getCustomID());
		}
		
    };
    
    private void preparePiggyBackList(){
    	piggybacked.clear();
        piggybacked.putAll(aliveNodes);
        piggybacked.putAll(failedNodes);
        piggybacked.putAll(suspectedNodes);
        Iterator<Map.Entry<Integer, PiggyBackElement>> entries = piggybacked.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, PiggyBackElement> entry = entries.next();
            //new node changes status, after enough dissemination
            if (entry.getValue().getDiseminateTimes()<0 && entry.getValue().getStatus()==NodeStatus.NEW){
            	entry.getValue().setStatus(NodeStatus.ALIVE);
            	entry.getValue().initDiseminateTimes();
            }
            if (entry.getValue().getDiseminateTimes()<0){
            	//don t disseminate if it has been disseminated enough
            	entries.remove();
            }else {
            	//otherwise disseminate
            	entry.getValue().dicreaseDisseminateTimes();
            	entry.getValue().incrementCounter();
            }
        }
    }
    
    private void mergeViews(Map<Integer,PiggyBackElement> view){
		Iterator<Map.Entry<Integer, PiggyBackElement>> entries = view.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Integer, PiggyBackElement> entry = entries.next();
			Integer key = entry.getKey();
			PiggyBackElement value = entry.getValue();
			if (value.getAddress().equals(selfAddress)) {
				// myself is suspected? Say no
				// implement later
				if (value.getStatus()== NodeStatus.SUSPECTED||value.getStatus()==NodeStatus.FAILED){
					value.initDiseminateTimes();
						aliveNodes.put(selfAddress.getId(), new PiggyBackElement(selfAddress, NodeStatus.ALIVE, value.getCount()));
				}
				//continue;
			}else if (value.getStatus()==NodeStatus.NEW){
				//if (!aliveNodes.containsKey(key)){
					value.initDiseminateTimes();
					//value.setStatus(NodeStatus.ALIVE);
					if (!neighbors.contains(value.getAddress())){
						neighbors.add(value.getAddress());						
					}
					failedNodes.remove(key);
					suspectedNodes.remove(key);
					aliveNodes.put(key, value);
			}
			else if ((value.getStatus() == NodeStatus.ALIVE)) {
				if (aliveNodes.containsKey(key)) {
					if (hasBiggerCount(value)) {
						//failedNodes.remove(key);
						//suspectedNodes.remove(key);
						aliveNodes.get(key).setCount(value.getCount());
					}
				}else if (suspectedNodes.containsKey(key)) {
					// see page 7
					// Such an Alive
					// message un-marks the suspected member ✡☞✌ in
					// membership
					// lists of recipient members
					if (hasBiggerCountSuspected(value)) {
						value.initDiseminateTimes();
						suspectedNodes.remove(key);
						//failedNodes.remove(key);
						aliveNodes.put(key, value);
					}
				} else if (failedNodes.containsKey(key)) {
					//alive does not override failed
				} else {
					// totally new node
					// neighbors.add(value.getAddress());
					value.initDiseminateTimes();
					aliveNodes.put(key, value);
				}
				// add to neigbors if it is not already there
			} else if (value.getStatus() == NodeStatus.FAILED) {
				// failed messages overrides alive and suspected,with any j
				if (aliveNodes.containsKey(key)) {
					aliveNodes.remove(key);
					value.initDiseminateTimes();
					failedNodes.put(key, value);
				}  else if (suspectedNodes.containsKey(key)) {
					suspectedNodes.remove(key);
					value.initDiseminateTimes();
					failedNodes.put(key, value);
				}
			} else if (value.getStatus() == NodeStatus.SUSPECTED) {
				// see beginning of page 7 in swim paper, anynode
				// receiving such message
				// also marks mj as suspected
				if (aliveNodes.containsKey(key)) {
					if (hasBiggerOrEqualCount(value)){
						value.setStatus(NodeStatus.SUSPECTED);
						value.initDiseminateTimes();
						value.setCount(value.getCount());
						
						//scheduleWaitingSuspected();
						//ids.put(suspectTimeoutId, value.getAddress());
						//failedNodes.remove(key);
						aliveNodes.remove(key);
						suspectedNodes.put(key, value);
					}
					
				}else if (suspectedNodes.containsKey(key)){
					if (hasBiggerCountSuspected(value)){
						//value.setStatus(NodeStatus.SUSPECTED);
						value.initDiseminateTimes();
						value.setCount(value.getCount());
						failedNodes.remove(key);
						suspectedNodes.put(key, value);
					}
				}
			}
		}
    	
    }
    
    private void checkSource(NatedAddress source){
    	//if we receive a message from a node, it means it is alive
    	//so we check if it is consistent with our data
    	if (aliveNodes.containsKey(source.getId())){
    		//information is consistent
    		//failedNodes.remove(source.getId());
    		//suspectedNodes.remove(source.getId());
    		return;
    	} 
    	else if (suspectedNodes.containsKey(source)){
    		PiggyBackElement e = suspectedNodes.get(source.getId());
    		e.setStatus(NodeStatus.ALIVE);
    		e.initDiseminateTimes();
    		//log.info("{} unsuspects node {} in source-checking",new Object[]{selfAddress.getId(),source.getId()});
    		suspectedNodes.remove(source.getId());
    		e.incrementCounter();
    		aliveNodes.put(source.getId(), e);
    		return;
    	}else if (failedNodes.containsKey(source)){
    		PiggyBackElement e = failedNodes.get(source.getId());
    		e.setStatus(NodeStatus.ALIVE);
    		e.initDiseminateTimes();
    		//log.info("{} unfailed node {} in source-checking",new Object[]{selfAddress.getId(),source.getId()});
    		e.incrementCounter();
    		failedNodes.remove(source.getId());
    		//suspectedNodes.remove(source.getId());
    		aliveNodes.put(source.getId(), e);
    		return;
    	}else {
    		if (!aliveNodes.containsKey(source.getId())){
    			//neighbors.add(source);    			
    			//log.info("{} adds new node {} in source-checking",new Object[]{selfAddress.getId(),source.getId()});
    			aliveNodes.put(source.getId(), new PiggyBackElement(source, NodeStatus.NEW));
    		}
        		if (!neighbors.contains(source)){
        			neighbors.add(source);    			
        			//log.info("{} adds new node {} in source-checking",new Object[]{selfAddress.getId(),source.getId()});
        			//aliveNodes.put(source.getId(), new PiggyBackElement(source, NodeStatus.NEW));
        		}
        		if (neighbors.size()==1){
        			schedulePeriodicPing();
        		}
    	}
    		//we have an unknown, totally new node!
    	
    	
    }
    
    private Handler<NetNatRequest> handleNetNatRequest = new Handler<NetNatRequest>(){

		@Override
		public void handle(NetNatRequest event) {
			// TODO Auto-generated method stub
			log.info("{} received request from nated node for new relays {}", new Object[]{selfAddress.getId(),event.getParents()});
			Set<NatedAddress> temp = new HashSet<NatedAddress>();
			for (NatedAddress ad: event.getParents()){
				if (aliveNodes.containsKey(ad.getId())){
					PiggyBackElement nodeAddress = aliveNodes.get(ad.getId());
					nodeAddress.setAddress(ad);
					aliveNodes.put(ad.getId(), nodeAddress);
					temp.add(nodeAddress.getAddress());
				}
			}
			trigger(new NetNatResponse(temp), nat);
		}
    	
    };
    
    private Handler<NetNatUpdate> handleNetNatUpdate = new Handler<NetNatUpdate>(){

		@Override
		public void handle(NetNatUpdate event) {
			// TODO Auto-generated method stub
			log.info("{} received update from nated node for new relay address {}", new Object[]{selfAddress.getId(),event.getSelfAddress()});
			if (aliveNodes.containsKey(selfAddress.getId())){
				
				PiggyBackElement element = aliveNodes.get(selfAddress.getId());
				element.setAddress(event.getSelfAddress());
				element.initDiseminateTimes();
				element.incrementCounter();
				selfAddress = event.getSelfAddress();
				neighbors.add(selfAddress);
				aliveNodes.put(event.getSelfAddress().getId(),element);
			}
		}
    	
    };
    
    private boolean hasBiggerCount(PiggyBackElement e){
    	if (e.getCount() > aliveNodes.get(e.getAddress().getId()).getCount()){
    		return true;
    	}
    	return false;
    	
    }
    
    private boolean hasBiggerOrEqualCount(PiggyBackElement e){
    	if (e.getCount() >= aliveNodes.get(e.getAddress().getId()).getCount()){
    		return true;
    	}
    	return false;
    	
    }
    
    private Boolean hasBiggerCountSuspected(PiggyBackElement e){
    	if (e.getCount() > suspectedNodes.get(e.getAddress().getId()).getCount()){
    		return true;
    	}
    	return false;
    	
    }   
    
    private PiggyBackElement selectRoundRobinNode(NatedAddress noReplyNode){
		Map<Integer, PiggyBackElement> tempMap = new HashMap<Integer, PiggyBackElement>();
		tempMap.putAll(aliveNodes);
		tempMap.putAll(suspectedNodes);
		if (noReplyNode != null) {
			tempMap.remove(noReplyNode.getId());
		}
//		for (NatedAddress address :ackids.values()){
//			tempMap.remove(address.getId());
//		}
		tempMap.remove(selfAddress.getId());
		List<Integer> keys = new ArrayList<Integer>(tempMap.keySet());
		PiggyBackElement value=null;
		if (keys.size()>0){
		if (robin < tempMap.size()) {
			Integer key = keys.get(robin);
			value = tempMap.get(key);
			robin++;
		} else {
			robin = 0;
			Integer key = keys.get(robin);
			value = tempMap.get(key);
		}
		}
		return value;
    }

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    private UUID scheduleWaitingAck(NatedAddress address) {
        ScheduleTimeout st = new ScheduleTimeout(2000);
        AckTimeout sc = new AckTimeout(st);
        st.setTimeoutEvent(sc);
        ackTimeoutId = sc.getTimeoutId();
	    ackids.put(ackTimeoutId,address);
        trigger(st, timer);
        return ackTimeoutId;
        //return ackTimeoutId;
    }
    
    //timeout for a node to be considered from alive to suspected
    private UUID scheduleWaitingSuspected(NatedAddress suspected) {
    	//we put bigger delay aas it is 2 RTT
        ScheduleTimeout st = new ScheduleTimeout(3000);
        SuspectedTimeout sc = new SuspectedTimeout(st);
        //sc.setCustomID(id);
        //log.info("UUID to be set: {} UUID setted: {}", new Object[]{id,sc.getCustomID()});
        st.setTimeoutEvent(sc);
        suspectids.put(sc.getTimeoutId(), suspected);
        trigger(st, timer);
        return sc.getTimeoutId();
        //return ackTimeoutId;
    }
    
    private void cancelWaitingAck(UUID id) {
        CancelTimeout cpt = new CancelTimeout(id);
        ackids.remove(id);
        trigger(cpt, timer);
    }
    
    private void cancelSuspected(UUID id){
    	CancelTimeout cpt = new CancelTimeout(id);
        suspectids.remove(id);
        trigger(cpt, timer);
    }
    

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(5000, 5000);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }
    
//    private int calculateDisseminateTimes(){
//    	Double d = (2) *Math.log10(10);
//    	return (d.intValue());
//    }

    public static class SwimInit extends Init<SwimComp> {

        private final NatedAddress selfAddress;
        private final Set<NatedAddress> bootstrapNodes;
        private final NatedAddress aggregatorAddress;

        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }

		public NatedAddress getSelfAddress() {
			return selfAddress;
		}

		public Set<NatedAddress> getBootstrapNodes() {
			return bootstrapNodes;
		}

		public NatedAddress getAggregatorAddress() {
			return aggregatorAddress;
		}
    }

    private static class StatusTimeout extends Timeout {

        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PingTimeout extends Timeout {

        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class AckTimeout extends Timeout{

		protected AckTimeout(ScheduleTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    
    }
    
    
    private static class SuspectedTimeout extends Timeout {
    	

		public SuspectedTimeout(ScheduleTimeout request) {
			super(request);
		}


    }
}
