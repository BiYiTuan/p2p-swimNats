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

import java.util.Random;
import java.lang.Math;
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

import se.kth.swim.msg.PiggyBackElement;
import se.kth.swim.msg.Ping;
import se.kth.swim.msg.Pong;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.NodeStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final NatedAddress selfAddress;
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
    
    private Map<UUID,NatedAddress> ids = new HashMap<UUID,NatedAddress>();
    
    Double lamdalogn;

    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.neighbors = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;
        this.aliveNodes=new HashMap<Integer,PiggyBackElement>();
        this.failedNodes=new HashMap<Integer,PiggyBackElement>();
        this.suspectedNodes=new HashMap<Integer,PiggyBackElement>();
        this.piggybacked=new HashMap<Integer,PiggyBackElement>();
        lamdalogn=((2) *Math.log10(neighbors.size()));
        for (NatedAddress address: neighbors){
        	this.aliveNodes.put(address.getId(),new PiggyBackElement(address,NodeStatus.ALIVE,0,calculateDisseminateTimes()));
        }
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
        subscribe(handleSuspectedTimeout,timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (!neighbors.isEmpty()) {
            	log.info("{} starting...", new Object[]{selfAddress.getId()});
            	piggybacked.put(selfAddress.getId(), new PiggyBackElement(selfAddress, NodeStatus.NEW, 0, calculateDisseminateTimes()));
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

        
    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
        	log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPings++;
            //if i get a ping from an unknown node, add it to the neighbor list
            if (!aliveNodes.containsKey(event.getHeader().getSource().getId())){
            	neighbors.add(event.getHeader().getSource());
            	aliveNodes.put(event.getHeader().getSource().getId(),new PiggyBackElement(event.getHeader().getSource(),NodeStatus.NEW,0,calculateDisseminateTimes()));
            	//now i have a node to ping....
            	if (neighbors.size()>0){
            		schedulePeriodicPing();
            	}
            }
            log.info("{} sending pong to partner:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            //clear previous piggybacking 
            piggybacked.clear();
            piggybacked.putAll(aliveNodes);
            piggybacked.putAll(failedNodes);
            piggybacked.putAll(suspectedNodes);
            //piggyback information about changes
            Iterator<Map.Entry<Integer, PiggyBackElement>> entries = piggybacked.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Integer, PiggyBackElement> entry = entries.next();
                if (entry.getValue().getDiseminateTimes()<0 && entry.getValue().getStatus()==NodeStatus.NEW){
                	entry.getValue().setStatus(NodeStatus.ALIVE);
                }
                if (entry.getValue().getDiseminateTimes()<0){
                	entries.remove();
                }else {
                	entry.getValue().dicreaseDisseminateTimes();
                	entry.getValue().incrementCounter();
                }
            }
            log.info("{} piggybacking:{} to {}", new Object[]{selfAddress.getId(),piggybacked.size(), event.getHeader().getSource()});
            trigger(new NetPong(selfAddress,event.getHeader().getSource(),new Pong(piggybacked,event.getContent().getSn())),network);
        }

    };
    
	private Handler<NetPong> handlePong = new Handler<NetPong>() {

		@Override
		public void handle(NetPong event) {
			// TODO Auto-generated method stub
			log.info("{} received pong from:{}",
					new Object[] { selfAddress.getId(),
							event.getHeader().getSource() });
			if ((event.getContent() != null)
					&& (ids.containsValue(event.getSource()) && (ids
							.containsKey(event.getContent().getSn())))) {
				// received pong from alive node
				if (suspectedNodes.containsKey(event.getSource().getId())) {
//					log.info(
//							"{} received pong from suspected node {}",
//							new Object[] { selfAddress.getId(),
//									event.getSource() });
					PiggyBackElement element = suspectedNodes.get(event.getSource().getId());
					element.setStatus(NodeStatus.ALIVE);
					element.setDiseminateTimes(calculateDisseminateTimes());
					log.info("{} unsuspecting node {}", new Object[] {
							selfAddress.getId(), event.getSource() });
					suspectedNodes.remove(event.getSource().getId());
					aliveNodes.put(event.getSource().getId(), element);
				}else if (failedNodes.containsKey(event.getSource().getId())){
					PiggyBackElement element = failedNodes.get(event.getSource().getId());
					element.setStatus(NodeStatus.ALIVE);
					element.setDiseminateTimes(calculateDisseminateTimes());
					log.info("{} unfailing node {}", new Object[] {
							selfAddress.getId(), event.getSource() });
					failedNodes.remove(event.getSource().getId());
					aliveNodes.put(event.getSource().getId(), element);
				}
				log.info("piggybacked pong {}", new Object[] { event
						.getContent().toString() });
				cancelWaitingAck(event.getContent().getSn());
				// get the piggybacked elements
				Iterator<Map.Entry<Integer, PiggyBackElement>> entries = event
						.getContent().getElements().entrySet().iterator();
				// should merge received elements with my elements
				while (entries.hasNext()) {
					Map.Entry<Integer, PiggyBackElement> entry = entries.next();
					Integer key = entry.getKey();
					PiggyBackElement value = entry.getValue();
					if (value.getAddress().equals(selfAddress)) {
						// myself is suspected? Say no
						// implement later
						continue;
					
					}else if (value.getStatus()==NodeStatus.NEW){
						if (!aliveNodes.containsKey(key)){
							value.setDiseminateTimes(calculateDisseminateTimes());
							value.setStatus(NodeStatus.ALIVE);
							aliveNodes.put(key, value);
							failedNodes.remove(key);
						}
					}
					else if ((value.getStatus() == NodeStatus.ALIVE)) {
						if (aliveNodes.containsKey(key)) {
							if ((value.getCount() > aliveNodes.get(key)
									.getCount())) {
								aliveNodes.put(key, value);
							}
						} else if (suspectedNodes.containsKey(key)) {
							// see page 7
							// Such an Alive
							// message un-marks the suspected member ✡☞✌ in
							// membership
							// lists of recipient members
							if ((value.getCount() > suspectedNodes.get(key)
									.getCount())) {
								value.setDiseminateTimes(calculateDisseminateTimes());
								value.setStatus(NodeStatus.ALIVE);
								suspectedNodes.remove(key);
								aliveNodes.put(key, value);
							}
						} else if (failedNodes.containsKey(key)) {

							//alive does not override failed
						} else {

							// totally new node
							// neighbors.add(value.getAddress());
							value.setDiseminateTimes(calculateDisseminateTimes());
							aliveNodes.put(key, value);
						}
						// add to neigbors if it is not already there
					} else if (value.getStatus() == NodeStatus.FAILED) {
						// failed messages overrides alive and suspected
						if (aliveNodes.containsKey(key)) {
							aliveNodes.remove(key);
							value.setDiseminateTimes(calculateDisseminateTimes());
							failedNodes.put(key, value);
						} else if (suspectedNodes.containsKey(key)) {
							suspectedNodes.remove(key);
							value.setDiseminateTimes(calculateDisseminateTimes());
							failedNodes.put(key, value);
						}
					} else if (value.getStatus() == NodeStatus.SUSPECTED) {
						// see beginning of page 7 in swim paper, anynode
						// receiving such message
						// also marks mj as suspected
						if (aliveNodes.containsKey(key)) {
							if (value.getCount()>=aliveNodes.get(key).getCount()){
								value.setStatus(NodeStatus.SUSPECTED);
								value.setDiseminateTimes(calculateDisseminateTimes());
								value.setCount(value.getCount());
								suspectedNodes.put(key, value);
								scheduleWaitingSuspected();
								ids.put(suspectTimeoutId, value.getAddress());
								aliveNodes.remove(key);
							}
							
						}else if (suspectedNodes.containsKey(key)){
							if (value.getCount()>suspectedNodes.get(key).getCount()){
								//value.setStatus(NodeStatus.SUSPECTED);
								value.setDiseminateTimes(calculateDisseminateTimes());
								value.setCount(value.getCount());
								suspectedNodes.put(key, value);
							}
						}
					}
				}
			}
		}

	};

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
        	//select random peer for bootstrap node
        	//should it be alive nodes only?
			Random random = new Random();
			Map<Integer,PiggyBackElement> tempMap = new HashMap<Integer,PiggyBackElement>();
			tempMap.putAll(aliveNodes);
			tempMap.putAll(suspectedNodes);
			List<Integer> keys = new ArrayList<Integer>(tempMap.keySet());
			log.info("temporary map size: alive {} suspected {} temp {}",new Object[]{aliveNodes.size(),suspectedNodes.size(),tempMap.size()});
			Integer randomKey = keys.get(random.nextInt(tempMap.size()));
			PiggyBackElement value = tempMap.get(randomKey);
			log.info("{} sending ping to partner:{}", new Object[] {
					selfAddress.getId(), value.getAddress() });
		    scheduleWaitingAck();
		    ids.put(ackTimeoutId,value.getAddress());
			trigger(new NetPing(selfAddress, value.getAddress(), new Ping(ackTimeoutId)), network);
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            log.info("{} sending status to aggregator:{} alive {} suspected {} failed {} total {}", new Object[]{selfAddress.getId(), aggregatorAddress,aliveNodes.size(),suspectedNodes.size(), failedNodes.size(),neighbors.size()});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings)), network);
        }

    };
    
    private Handler<AckTimeout> handleAckTimeout = new Handler<AckTimeout>(){

		@Override
		public void handle(AckTimeout event) {
			// TODO Auto-generated method stub
			log.info("{} timeout - no reply from: {} UUID: {} NODE SUSPECTED!", new Object[]{selfAddress.getId(), ids.get(event.getTimeoutId()),event.getTimeoutId()});
			Integer addressid = ids.get(event.getTimeoutId()).getId();
			cancelWaitingAck(event.getTimeoutId());
			for (Integer key : aliveNodes.keySet()) {
			    System.out.println("Key = " + key);
			}
			if (aliveNodes.containsKey(addressid)){
				//declare as suspected, not failed
				PiggyBackElement element =(PiggyBackElement) aliveNodes.get(addressid);
				//log.info("piggyback element: {}",element.toString());
				element.setDiseminateTimes(calculateDisseminateTimes());
				//change status to suspected
				element.setStatus(NodeStatus.SUSPECTED);
				suspectedNodes.put(addressid, element);
				aliveNodes.remove(addressid);
				
			}
			scheduleWaitingSuspected(event.getTimeoutId());
//			}else if (suspectedNodes.containsKey(addressid)){
//				PiggyBackElement element =(PiggyBackElement) suspectedNodes.get(addressid);
//				log.info("piggyback element: {}",element.toString());
//				element.setDiseminateTimes(calculateDisseminateTimes());
//				element.setStatus(NodeStatus.ALIVE);
//				aliveNodes.put(addressid, element);
//				suspectedNodes.remove(addressid);
//			}
			//ids.remove(event.getTimeoutId());
		}
    	
    };
    
    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>(){

		@Override
		public void handle(SuspectedTimeout event) {
			// TODO Auto-generated method stub
			log.info("{} suspecting timeout - no reply from: {} NODE FAILED!", new Object[]{selfAddress.getId(), ids.get(event.getCustomID())});
//			log.info("{} CUSTOM UUID IS: {}", new Object[]{selfAddress.getId(),event.getCustomID()});
//			for (UUID id: ids.keySet()){
//				System.out.println(id.toString());
//			}
			Integer addressId = ids.get(event.getCustomID()).getId();
			if (suspectedNodes.containsKey(addressId)){
				PiggyBackElement element = (PiggyBackElement) suspectedNodes.get(addressId);
				element.setDiseminateTimes(calculateDisseminateTimes());
				element.setStatus(NodeStatus.FAILED);
				suspectedNodes.remove(addressId);
				failedNodes.put(addressId, element);
			}
			ids.remove(event.getCustomID());
			cancelSuspected(event.getCustomID());
			
		}
		
    	
    };
    
    
    

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    private void scheduleWaitingAck() {
        ScheduleTimeout st = new ScheduleTimeout(3000);
        AckTimeout sc = new AckTimeout(st);
        st.setTimeoutEvent(sc);
        ackTimeoutId = sc.getTimeoutId();
        trigger(st, timer);
        //return ackTimeoutId;
    }
    
    //timeout for a node to be considered from suspected to failed
    private void scheduleWaitingSuspected(UUID id) {
        ScheduleTimeout st = new ScheduleTimeout(3000);
        SuspectedTimeout sc = new SuspectedTimeout(st,id);
        sc.setCustomID(id);
        //log.info("UUID to be set: {} UUID setted: {}", new Object[]{id,sc.getCustomID()});
        st.setTimeoutEvent(sc);
        trigger(st, timer);
        //return ackTimeoutId;
    }
    
    private void scheduleWaitingSuspected(){
    	ScheduleTimeout st = new ScheduleTimeout(3000);
        SuspectedTimeout sc = new SuspectedTimeout(st);
        //sc.setCustomID(id);
        //log.info("UUID to be set: {} UUID setted: {}", new Object[]{id,sc.getCustomID()});
        suspectTimeoutId = sc.getTimeoutId();
        sc.setCustomID(suspectTimeoutId);
        st.setTimeoutEvent(sc);
        trigger(st, timer);
    }
    
    private void cancelWaitingAck(UUID id) {
        CancelTimeout cpt = new CancelTimeout(id);
        trigger(cpt, timer);
    }
    
    private void cancelSuspected(UUID id){
    	CancelTimeout cpt = new CancelTimeout(id);
        ids.remove(id);
        trigger(cpt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
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
    
    private int calculateDisseminateTimes(){
    	Double d = (2) *Math.log10(neighbors.size());
    	return (d.intValue());
    }

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
    	
    	private UUID customID;

		public SuspectedTimeout(ScheduleTimeout request) {
			super(request);
		}

		protected SuspectedTimeout(ScheduleTimeout request,UUID customID) {
			super(request);
			this.customID=customID;
			// TODO Auto-generated constructor stub
		}

		public UUID getCustomID() {
			return customID;
		}

		public void setCustomID(UUID customID) {
			this.customID = customID;
		}
		
		
    	
    }
}
