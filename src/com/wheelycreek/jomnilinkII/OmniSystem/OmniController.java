/** Object model for Omni system.
  */
/*  Copyright (C) 2010 Michael Geddes
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, version 2
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
package com.wheelycreek.jomnilinkII.OmniSystem;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

import com.digitaldan.jomnilinkII.Connection;
import com.digitaldan.jomnilinkII.Message;
import com.digitaldan.jomnilinkII.NotificationListener;
import com.digitaldan.jomnilinkII.OmniInvalidResponseException;
import com.digitaldan.jomnilinkII.OmniNotConnectedException;
import com.digitaldan.jomnilinkII.OmniUnknownMessageTypeException;
import com.digitaldan.jomnilinkII.MessageTypes.CommandMessage;
import com.digitaldan.jomnilinkII.MessageTypes.NameData;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectProperties;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectStatus;
import com.digitaldan.jomnilinkII.MessageTypes.OtherEventNotifications;
import com.digitaldan.jomnilinkII.MessageTypes.SystemFeatures;
import com.digitaldan.jomnilinkII.MessageTypes.SystemFormats;
import com.digitaldan.jomnilinkII.MessageTypes.SystemInformation;
import com.digitaldan.jomnilinkII.MessageTypes.SystemStatus;
import com.digitaldan.jomnilinkII.MessageTypes.SystemTroubles;
import com.digitaldan.jomnilinkII.MessageTypes.ZoneReadyStatus;
import com.digitaldan.jomnilinkII.MessageTypes.events.OtherEvent;
import com.digitaldan.jomnilinkII.MessageTypes.events.UserMacroButtonEvent;
import com.digitaldan.jomnilinkII.MessageTypes.properties.AuxSensorProperties;
import com.digitaldan.jomnilinkII.MessageTypes.properties.ButtonProperties;
import com.digitaldan.jomnilinkII.MessageTypes.properties.UnitProperties;
import com.digitaldan.jomnilinkII.MessageTypes.properties.ZoneProperties;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.AuxSensorStatus;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.Status;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.UnitStatus;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.ZoneStatus;
import com.wheelycreek.jomnilinkII.OmniNotifyListener;
import com.wheelycreek.jomnilinkII.OmniPart;
import com.wheelycreek.jomnilinkII.OmniPart.NameChangeMessage;
import com.wheelycreek.jomnilinkII.Parts.OmniButton;
import com.wheelycreek.jomnilinkII.Parts.OmniDevice;
import com.wheelycreek.jomnilinkII.Parts.OmniOutput;
import com.wheelycreek.jomnilinkII.Parts.OmniRoom;
import com.wheelycreek.jomnilinkII.Parts.OmniSensor;
import com.wheelycreek.jomnilinkII.Parts.OmniFlag;
import com.wheelycreek.jomnilinkII.Parts.OmniUnit;
import com.wheelycreek.jomnilinkII.Parts.OmniZone;



/** Root of object model for an Omni controller.
 *
 * @author michaelg
 */
public class OmniController implements OmniNotifyListener {
	public static void main(String[] args) {
		
		if(args.length != 3){
			System.out.println("Usage:com.wheelycreek.jomnilinkII.OmiController host port encKey");
			System.exit(-1);
		}
		String host  = args[0];
		int port = Integer.parseInt(args[1]);
		String key = args[2];
		
		try {
			OmniController c = new OmniController(host,port,key);
			c.setDebugChan(dcSensors | dcZones /*dcMessage*/ |dcChildMessage, true);
			c.reloadProperties();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/// Debug channels (all)
	static final int dcAll = 0x3f;
	/// Debug channel for connections
	static final int dcConnection = 0x1;
	/// Debug channel for messsages
	static final int dcMessage = 0x2;
	/// Debug channel for zones
	static final int dcZones = 0x4;
	/// Debug channel for child messages
	static final int dcChildMessage = 0x8;
	/// Debug channel for sensors
	static final int dcSensors = 0x10;
	/// Debug channel for units
	static final int dcUnits = 0x20;
	private int debug_channels;

	/** Check all the specified debug channels are set.
	  */
	public boolean getDebugChan( int channels ) {
		return (debug_channels & channels) == channels;
	}
	/** Set the specified debug channels.
	  */
	public void setDebugChan( int channels, boolean newVal) {
		if (newVal)
			debug_channels |= channels;
		else
			debug_channels &= ~channels;
		if (((channels & dcConnection) == dcConnection) && (omni != null))
			omni.debug = newVal;
	}

	/** The specified omni host.
	 */
	private String omni_host;
	/** The specified omni port.
	 */
	private int omni_port;
	/** The current key (for use with reconnect)
	 */
	private String omni_key;

	// Collections of names.
	private	Vector<String> zone_names;
	private Vector<String> unit_names;
	private Vector<String> area_names;

	// Collections of Omni parts.
	private SortedMap<Integer, OmniZone> zones;
	private SortedMap<Integer, OmniSensor> sensors;
	private SortedMap<Integer, OmniUnit> units;
	private SortedMap<Integer, OmniOutput> outputs;
	private SortedMap<Integer, OmniDevice> devices;
	private SortedMap<Integer, OmniRoom> rooms;
	private SortedMap<Integer, OmniFlag> flags;
	private SortedMap<Integer, OmniButton> buttons;
	
	// Various one-off bits of system information.
	private SystemFeatures    sys_features;
	private SystemFormats     sys_formats;
	private SystemInformation sys_info;
	private SystemStatus      sys_status;
	private SystemTroubles    sys_troubles;
	private ZoneReadyStatus   zones_ready;
	
	/** Construct required arrays.
	  * Called by constructors.
	  */
	private void constructArrays() {
		notificationListeners = new Vector<OmniNotifyListener>();
		zones   = new TreeMap<Integer, OmniZone>();
		sensors = new TreeMap<Integer, OmniSensor>();
		units   = new TreeMap<Integer, OmniUnit>();
		outputs = new TreeMap<Integer, OmniOutput>();
		devices = new TreeMap<Integer, OmniDevice>();
		rooms   = new TreeMap<Integer, OmniRoom>();
		flags   = new TreeMap<Integer, OmniFlag>();
		buttons = new TreeMap<Integer, OmniButton>();
	}
	
	/** Constrcut an omni controller.
	  * @param host The name of the host
	  * @param port The port to connect to.
	  * @param key  The security key to use.
	  */
	public OmniController(String host, int port, String key) throws UnknownHostException, IOException, Exception {
		constructArrays();
		createConnection(host, port, key);
		this.omni_host = host;
		this.omni_port = 0;
		this.omni_key = "";
	}
	/** Constrcut an omni controller.
	  * @param host The name of the host
	  * @param port The port to connect to.
	  * @param key  The security key to use.
	  * @param keepkey True to keep the key for reconnects.
	  */
	public OmniController(String host, int port, String key, boolean keepKey) throws UnknownHostException, IOException, Exception {
		constructArrays();
		createConnection(host, port, key);
		this.omni_host = host;
		if (keepKey) {
			this.omni_port = port;
			this.omni_key = key;
		} else {
			this.omni_port = 0;
			this.omni_key = "";
		}
	}
	
	protected Connection omni;
	
	/** Create a connection to an omni.
	  */
	protected void createConnection(String host, int port, String key) throws UnknownHostException, IOException, Exception{
		omni = new Connection(host, port, key);
		omni.debug = getDebugChan(dcConnection);
		omni.addNotificationListener(new NotificationListener(){
			@Override
			public void objectStausNotification(ObjectStatus s) { statusNotify(s); }
			@Override
			public void otherEventNotification(OtherEventNotifications o) {	otherEventNotify(o);}
		});
		omni.enableNotifications();
		// Reset Information so that it is read again.
		sys_info = null;
		sys_status = null;
		sys_troubles = null;
		sys_formats = null;
		sys_features = null;
		zones_ready = null;
	}
	/** Reconnect to the omni if allowed.
	  */
	protected  boolean reconnect() throws UnknownHostException, IOException, Exception {
		if (omni_key == null)
			return false;
		else {
			if (omni.connected())
				omni.disconnect();
			createConnection(omni_host,omni_port,omni_key);
			// TODO: Reload the status properties. 
			
			return omni.connected();
			
		}
	}
	/** Get the capacity of an omni area.
	 * @param area
	 * @return The number of objects of the specified type.
	 */
	protected int getCapacity( OmniArea area) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		// Capacity for sensors is the same for zones. It's just about the types.
		// (Querying for sensors will result in an error).
		if (area == OmniArea.Sensor)
			area = OmniArea.Zone;
		return omni.reqObjectTypeCapacities(area.get_objtype_msg()).getCapacity();
	}
	
	/** Reload all properties (including names and types).
	 * @throws Exception 
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	  */
	public void reloadProperties() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException, Exception {
		loadZones();
		loadSensors();
		loadUnits();
		loadButtons();
	}
	/** Reload the status for the parts.
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	  */
	public void reloadStatus() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		updateZones();
		updateSensors();
	}
	
	/** Receive status notifications from the communications layer.
	 * @param s The status object for the area.
	 */
	protected void statusNotify(ObjectStatus s) {
		OmniArea area = OmniArea.fromMessageType(s.getStatusType());
		if (getDebugChan(dcMessage)) {
			System.out.println( area.toString()+" changed");
			System.out.println(s.toString());
		}
				
		switch (area) {
		case Area:
			break;
		case AudioZone:
			break;
		case Sensor: {
			Status status[]  = s.getStatuses();
			for (int i=0; i< status.length; ++i) {
				AuxSensorStatus ass = (AuxSensorStatus)status[i];
				sensorStatusReceive(ass);
			}
		} break;
		case ExpEnclosure:
			break;
		case Msg:
			break;
		case Thermo:
			break;
		case Unit: {
			Status status[]	= s.getStatuses();

			for (int i = 0; i< status.length; ++i) {
				UnitStatus zs = (UnitStatus) status[i];
				unitStatusReceive(zs);
			}
		}
		break;
		case Zone: {
			Status status[] = s.getStatuses();

			for (int i = 0; i< status.length; ++i) {
				ZoneStatus zs = (ZoneStatus) status[i];
				zoneStatusReceive(zs);
			}
		}
		break;
		default:
			System.out.println("Unknown type " + s.getStatusType());
		break;
		}
	}
	
	/** Receive a Unit status change message.
	 * @param status
	 */
	private void unitStatusReceive(UnitStatus status) {
		if (getDebugChan(dcUnits))
			System.out.println("Unit Changed: "+status.toString());
		
		OmniUnit unit = getUnit(status.getNumber());
		if (unit != null)
			unit.update(status, NotifyType.Notify);
	}

	/** Receive a sensor status change.
	  */
	private void sensorStatusReceive(AuxSensorStatus status) {
		
		if (getDebugChan(dcSensors))
			System.out.println("Sensor Changed: "+status.toString());

		OmniSensor sensor = sensors.get(status.getNumber());
		if (sensor != null)
			sensor.update(status, NotifyType.Notify);
		
	}
	/** Receive a list 'other event' notification.
	  * calls otherEventReceive for each one.
	  */
	protected void otherEventNotify(OtherEventNotifications o) {
		for(int k=0;k<o.Count();k++){
			otherEventReceive(o.getNotification(k));
		}
	}
	
	
	/** get a zone, load it with information.
	 * @param zonenr The zone to load.
	 * @return a loaded zone.
	 * @throws Exception 
	 * @throws OmniNotConnectedException 
	 */
	public OmniZone getZone(int zonenr) throws OmniNotConnectedException, Exception {
		OmniZone result = zones.get(zonenr);
		if (result == null) {
			// Build a new 
			loadZones(zonenr, zonenr);
			result = zones.get(zonenr);
		}
		return result;
	}

	/** Load all available zones as objects.
	  */
	protected void loadZones() throws Exception {
		loadZones(1,0);
	}
	/** Load a range of zones as objects
	  */
	protected void loadZones(int startZone, int endZone) throws Exception {
		
		if (endZone <= 0)
			endZone = getCapacity(OmniArea.Zone);
		else if (endZone < startZone)
			endZone =  startZone;

		int objnum = startZone-1;
		Message m;
		while((m = omni.reqObjectProperties(Message.OBJ_TYPE_ZONE, objnum, 1, 
				ObjectProperties.FILTER_1_NAMED, ObjectProperties.FILTER_2_AREA_ALL, ObjectProperties.FILTER_3_ANY_LOAD)).getMessageType() 
				== Message.MESG_TYPE_OBJ_PROP){
			ZoneProperties zp = (ZoneProperties)m;
			objnum = zp.getNumber();
			OmniZone zone = zones.get(objnum);
			if (zone == null) {
				zone = new OmniZone(objnum);
				zones.put(objnum, zone);
				zone.addNotificationListener(this);
			}
			zone.update(zp, NotifyType.Initial);
		}
	}

	/** Update status of all loaded zones.
	  */
	protected void updateZones() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		ObjectStatus status = omni.reqObjectStatus(OmniArea.Zone.get_objtype_msg(), 1,getCapacity(OmniArea.Zone));
		ZoneStatus [] zonestats = (ZoneStatus[])status.getStatuses();
		for (ZoneStatus zonestat : zonestats) {
			int zoneidx = zonestat.getNumber();
			OmniZone zone = zones.get(zoneidx);
			if (zone != null)
				zone.update(zonestat, NotifyType.Notify );
		}
	}
	
	/** Receive a zone status.
	  */
	protected void zoneStatusReceive( ZoneStatus status) {
		if (getDebugChan(dcZones))
			System.out.println("Zone Changed: "+status.toString());
		OmniZone zone = zones.get(status.getNumber());
		if (zone != null)
			zone.update(status,NotifyType.Notify);
	}

	/** Get a sensor.
	  load if necessary.
	  */
	public OmniSensor getSensor(int sensorNo) throws Exception, IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		OmniSensor ret=sensors.get(sensorNo);
		if (ret == null) {
			loadSensors(sensorNo,sensorNo);
			ret = sensors.get(sensorNo);
		}
		return ret;
	}

	/** Load all available (named) sensors.
	 */
	protected void loadSensors() throws Exception, IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		loadSensors(1,-1);
	}
	protected void loadSensors(int fromObj, int toObj) throws Exception, IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		int objnum = fromObj-1;
		if (objnum < 0) objnum = 0;
		Message m;
		while((m = omni.reqObjectProperties(Message.OBJ_TYPE_AUX_SENSOR, objnum, 1, 
				ObjectProperties.FILTER_1_NAMED, ObjectProperties.FILTER_2_AREA_ALL, ObjectProperties.FILTER_3_NONE)).getMessageType() 
				== Message.MESG_TYPE_OBJ_PROP){
			AuxSensorProperties op = (AuxSensorProperties)m; 	
			objnum = op.getNumber();
			OmniSensor sensor= sensors.get(objnum);
			if (sensor == null) {
				sensor = new OmniSensor(objnum);
				sensors.put(objnum, sensor);
				sensor.addNotificationListener(this);
				sensor.update(op, NotifyType.Initial);
			}
			if (toObj > 0 && objnum >= toObj)
				break;
		}
		
	}
	
	/** Update the status of all loaded sensors.
	 */
	protected void updateSensors() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		// Update all sensor values.
		Iterator<OmniSensor> iter = sensors.values().iterator();
		while (iter.hasNext()) {
			OmniSensor sense = iter.next();
			if (sense != null) {
				ObjectStatus status = omni.reqObjectStatus(OmniArea.Sensor.get_objtype_msg(), sense.number,sense.number);
				AuxSensorStatus [] sensorstats = (AuxSensorStatus[])status.getStatuses();
				if (sense.number == sensorstats[0].getNumber())
					sense.update(sensorstats[0], NotifyType.Notify);
			}
		}
	}
	
	/** Get at a unit object.
	  This includes outputs, rooms, devices and flags.
	  */
	public OmniUnit getUnit(int unitNo) {
		OmniUnit ret = units.get(unitNo);
		if (ret == null) {
			ret = outputs.get(unitNo);
			if (ret == null) {
				ret = rooms.get(unitNo);
				if (ret == null ) {
					ret = devices.get(unitNo);
					if (ret == null ) {
						ret = flags.get(unitNo);
					}
				}
			}
		}
		return ret;
	}
	/** Get an Output object by number.
	  */
	public OmniOutput getOutput(int outputNo) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		OmniOutput ret=outputs.get(outputNo);
		if (ret == null) {
			loadUnits(outputNo,outputNo);
			ret = outputs.get(outputNo);
		}
		return ret;
	}
	/** Get a Room object by number.
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	  */
	public OmniRoom getRoom(int roomNo) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		OmniRoom ret=rooms.get(roomNo);
		if (ret == null) {
			loadUnits(roomNo,roomNo);
			ret = rooms.get(roomNo);
		}
		return ret;
	}
	/** Get a Device object by number.
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	  */
	public OmniDevice getDevice(int deviceNo) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		OmniDevice ret=devices.get(deviceNo);
		if (ret == null) {
			loadUnits(deviceNo,deviceNo);
			ret = devices.get(deviceNo);
		}
		return ret;
	}
	/** Get a flag object by number.
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	  */
	public OmniFlag getFlag(int flagNo) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		OmniFlag ret=flags.get(flagNo);
		if (ret == null) {
			loadUnits(flagNo,flagNo);
			ret = flags.get(flagNo);
		}
		return ret;
	}
	
	/** Load all units.
	  */
	protected void loadUnits() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		loadUnits(1,-1);
	}
	
	/** Load  a range of unit objects.
	    Includes all types of units.
	  */
	protected void loadUnits( int fromUnit, int toUnit ) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		int objnum = fromUnit-1;
		if (objnum < 0) objnum = 0;
		Message m;
		// Get initial properties
		while((m = omni.reqObjectProperties(OmniArea.Unit.get_objtype_msg(), objnum, 1, 
				ObjectProperties.FILTER_1_NAMED, ObjectProperties.FILTER_2_AREA_ALL, ObjectProperties.FILTER_3_ANY_LOAD)).getMessageType() 
				== Message.MESG_TYPE_OBJ_PROP){
			UnitProperties uprop = (UnitProperties)m;
			objnum = uprop.getNumber();
			OmniUnit unit = null;
			switch (OmniUnit.UnitType.typeAsEnum(uprop.getUnitType())) {
				case UPB:
				case HLCLoad:
				case RadioRA:
				case ViziaRFLoad:
				case CentraLite: {
					OmniDevice device = devices.get(objnum);
					if (device == null) {
						device = new OmniDevice(objnum);
						devices.put(objnum, device);
						device.addNotificationListener(this);
					}
					unit = device;
				} break;
				case Output: {
					OmniOutput output = outputs.get(objnum);
					if (output == null) {
						output = new OmniOutput(objnum);
						outputs.put(objnum, output);
						output.addNotificationListener(this);
					}
					unit = output;
				}break;
				case HLCRoom:
				case ViziaRFRoom:{
					OmniRoom room = rooms.get(objnum);
					if (room == null) {
						room = new OmniRoom(objnum);
						rooms.put(objnum, room);
						room.addNotificationListener(this);
					}
					unit = room;
				} break;
				case Flag: {
					OmniFlag flag = flags.get(objnum);
					if (flag == null) {
						flag = new OmniFlag(objnum);
						flags.put(objnum, flag);
						flag.addNotificationListener(this);
					}
					unit = flag;
				} break;		
				case AudioZone:
				case AudioSource: 
				default:{
					unit = units.get(objnum);
					if (unit == null) {
						unit = new OmniUnit(objnum);
						units.put(objnum, unit);
						unit.addNotificationListener(this);
					}
				}
			}
			if (unit != null)
				unit.update(uprop, NotifyType.Initial);
			if (toUnit > 0 && objnum >= toUnit)
				break;
		}
	}

	/** Load buttons.
	  */
	protected void loadButtons() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
	    loadButtons(1,-1);
	}
	/** Load a range of buttons.
	  */
	protected void loadButtons(int objFrom, int objTo) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		int objnum = objFrom-1;
		Message m;
		// Get initial button properties
		while((m = omni.reqObjectProperties(OmniArea.Button.get_objtype_msg(), objnum, 1, 
				ObjectProperties.FILTER_1_NAMED, ObjectProperties.FILTER_2_AREA_ALL, ObjectProperties.FILTER_3_NONE)).getMessageType() 
				== Message.MESG_TYPE_OBJ_PROP){
			ButtonProperties bprop = (ButtonProperties)m;
			objnum = bprop.getNumber();
			OmniButton button = buttons.get(objnum);
			if (button == null) {
				button = new OmniButton(objnum);
				buttons.put(objnum, button);
				button.addNotificationListener(this);
			}
			if (objTo > 0 && objnum >= objTo )
				break;
		}
	}

	public static OmniNotifyListener.NotifyType msgType(boolean isInitial) {
		return isInitial?OmniNotifyListener.NotifyType.Initial:OmniNotifyListener.NotifyType.Notify;
	}
	/** Receive a single 'OtherEvent' type message.
	  */
	protected void otherEventReceive( OtherEvent event) {
		if (getDebugChan(dcMessage))
			System.out.println(event.toString());	
		switch (event.getEventType()) {
			case UserMacroButton: {
				OmniButton ob = buttons.get(((UserMacroButtonEvent)event).getButtonNumber());
				if (ob != null) {
					ob.notifyPress();
				}
			}
			case ProlinkMessage:
			case CentraliteSwitch:
			case Alarm:
			case ComposeCode:
			case X10Code:
			case SecurityArming:
			case LumniaModeChange:
			case UnitSwitchPress:
			case UPBLink:
			case AllSwitch:
			case PhoneLine:
			case Power:
			case DCM:
			case EnergyCost:
				break;
		}
	}
	
	protected void load_vector(OmniArea area, Vector<String> list, boolean reload) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		int max_number = getCapacity(area);
		int first= reload?list.size():0;
		list.setSize(max_number);
		for(int i = first; i < max_number; ++i) {
			Message msg = omni.receiveName(area.get_objtype_msg(), i);
			if (msg instanceof NameData ) {
				NameData nameMsg = (NameData)msg;
				list.set(i, nameMsg.getName());
			}
		}
	}
	protected Vector<String> create_loaded_vector(OmniArea area) throws OmniNotConnectedException, Exception {
		Vector<String> result = new Vector<String>();
		try {
			load_vector(area, result, false);
		} catch (OmniNotConnectedException e) {
			// connect again?
			if (!reconnect())
				throw e;
			
		} catch (OmniUnknownMessageTypeException e) {
			// Really shouldn't get this
			e.printStackTrace();
		}
		return result;
	}
	
	/** Get at vectors of names, loaded with names.
	 * This allows access to just the names.
	 * @param area
	 * @return
	 * @throws OmniNotConnectedException
	 * @throws Exception
	 */
	protected Vector<String> get_vectors(OmniArea area) throws OmniNotConnectedException, Exception {
		switch (area) {
		case Zone:
			if (zone_names == null)
			  zone_names = create_loaded_vector(area);
			return zone_names;
		case Unit:
			if (unit_names == null)
			  unit_names = create_loaded_vector(area);
			return unit_names;
		case Area:
			if (area_names == null)
			  area_names = create_loaded_vector(area);
			return area_names;
		default:
			return null;
		}
	}

	// 
	protected void setName( OmniArea area, int index, String name ) throws OmniNotConnectedException, Exception {
		Vector<String> vectors = get_vectors(area);
		if (vectors != null) {
			if (index >= vectors.size())
				vectors.setSize(index);
			vectors.set(index-1, name);
		}
	}
	public String getName(OmniArea area, int index) throws OmniNotConnectedException, Exception {
		if (index < 0)
			return null;
		OmniPart part = null;
		switch (area) {
		case Zone:     part = zones.get(index); break;
		case Button:   part = buttons.get(index); break;
		case Sensor:   part = sensors.get(index); break;
		case Unit: 
			part = getUnit(index); break;
		}
		if (part != null)
			return part.getName();
		
		Vector<String> vectors = get_vectors(area);
		if (vectors != null)
			return vectors.get(index-1);
		else
			return null;
	}
	

	public String getZoneName(int index) throws OmniNotConnectedException, Exception {
		return getName(OmniArea.Zone, index);
	}
	public String getUnitName(int index) throws OmniNotConnectedException, Exception {
		return getName(OmniArea.Unit, index);
	}
	public String getAreaName(int index) throws OmniNotConnectedException, Exception {
		return getName(OmniArea.Area, index);
	}
	/**
	 * @param sys_features the sys_features to set
	 */
	public void setFeatures(SystemFeatures sys_features) {
		this.sys_features = sys_features;
	}
	/**
	 * @return the sys_features
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public SystemFeatures getFeatures() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (sys_features == null)
			sys_features = omni.reqSystemFeatures();
		return sys_features;
	}
	/**
	 * @param sys_formats the sys_formats to set
	 */
	protected void setFormats(SystemFormats sys_formats) {
		this.sys_formats = sys_formats;
	}
	/**
	 * @return the sys_formats
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public SystemFormats getFormats() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (sys_formats == null)
			sys_formats = omni.reqSystemFormats();
		return sys_formats;
	}
	/**
	 * @param sys_info the sys_info to set
	 */
	protected void setInfo(SystemInformation sys_info) {
		this.sys_info = sys_info;
	}
	/**
	 * @return the sys_info
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public SystemInformation getInfo() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (sys_info == null)
			sys_info = omni.reqSystemInformation();
		return sys_info;
	}
	/**
	 * @param sys_status the sys_status to set
	 */
	public void setStatus(SystemStatus sys_status) {
		this.sys_status = sys_status;
	}
	/**
	 * @return the sys_status
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public SystemStatus getStatus() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (sys_status == null)
			sys_status = omni.reqSystemStatus();
		return sys_status;
	}
	/**
	 * @param sys_troubles the sys_troubles to set
	 */
	public void setTroubles(SystemTroubles sys_troubles) {
		this.sys_troubles = sys_troubles;
	}
	/**
	 * @return the sys_troubles
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public SystemTroubles getTroubles() throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (sys_troubles == null)
			sys_troubles = omni.reqSystemTroubles();
		return sys_troubles;
	}
	private void initZoneReady() throws Exception, IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		
		try {
			zones_ready = omni.reqZoneReadyStatus();
		} catch (OmniNotConnectedException e) {
			if (reconnect())
				zones_ready = omni.reqZoneReadyStatus();
		}
	}
	/**
	 * @return the zones_ready
	 * @throws OmniUnknownMessageTypeException 
	 * @throws OmniInvalidResponseException 
	 * @throws OmniNotConnectedException 
	 * @throws IOException 
	 */
	public boolean getZoneReady( int zone) throws Exception, IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (zones_ready == null)
			initZoneReady();
		
		return (zones_ready.getZoneReady(zone));
	}

	private Vector<OmniNotifyListener> notificationListeners;
	
	public void addNotificationListener(OmniNotifyListener listener){
		synchronized (notificationListeners) {
			notificationListeners.add(listener);
		}
	}

	public void removeNotificationListener(OmniNotifyListener listener){
		synchronized (notificationListeners) {
			if(notificationListeners.contains(listener))
				notificationListeners.remove(listener);
		}
	}
	
	protected void sendAction( ActionRequest msg ) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		omni.controllerCommand(msg.getCommand());
	}
	
	/** Respond to a change type request from an OmniPart
	 * @param msg  Message from omni-part with notifyType==ChangeRequest
	 */
	protected void objectChangeRequest(ChangeMessage msg) throws IOException, OmniNotConnectedException, OmniInvalidResponseException, OmniUnknownMessageTypeException {
		if (msg instanceof NameChangeMessage ) {
			// Special type; name change applies to all areas.
			omni.sendName(msg.area.get_objtype_msg(), msg.number, ((NameChangeMessage) msg).name);
		} else if (msg instanceof ActionRequest) {
			sendAction((ActionRequest)msg);
		} else { 
			switch (msg.area) {
			case Button: {
				// Create a CommandMessage to send a macro button press.
				sendAction(new ActionRequest(msg.area, msg.number, CommandMessage.macroButtonCmd(msg.number)));
			} break;	
			case Unit: {
				OmniUnit.UnitChangeMessage ucm = (OmniUnit.UnitChangeMessage)msg;
				switch (ucm.changeType) {
				case RawState:
					OmniUnit unit = units.get(ucm.number);
					if (unit != null) { // which it should never be.
						switch ( unit.getUnitType()) {
						case Flag:
							sendAction( new ActionRequest(msg.area, msg.number, CommandMessage.unitSetCounterCmd(unit.number, unit.getRawStatus())));
						}
					}
				}
				// TODO: Change the value of a unit. (optionally for a specified time)
			} break;
			case Sensor:{
				//OmniSensor.SensorChangeMessage scm = (OmniSensor.SensorChangeMessage)msg;
				// TODO: Change min/max on the sensors.
				
			} break;
			}
		}
	}
	
	@Override
	public void objectChangedNotification(ChangeMessage msg) {
		if (getDebugChan(dcChildMessage))
			try {
				String areaname = getName(msg.area, msg.number);
				System.out.println(msg.area.toString()+" '"+areaname +"': "+ msg.toString());
			} catch (OmniNotConnectedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		if (msg.notifyType == NotifyType.ChangeRequest) {
			// A change has been requested to be sent to the omni.
			try {
				objectChangeRequest(msg);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (OmniNotConnectedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (OmniInvalidResponseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (OmniUnknownMessageTypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
// vim: syntax=java.doxygen ts=4 sw=4 noet
