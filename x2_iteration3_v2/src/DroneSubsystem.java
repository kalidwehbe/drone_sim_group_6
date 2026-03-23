import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DroneSubsystem {

    private final int id;
    private final InetAddress schedulerAddress;
    private final int schedulerPort;

    private DatagramSocket socket;
    private final DroneStateMachine fsm;

    private int droneX = 0;
    private int droneY = 0;
    private int agent = 14;


    public DroneSubsystem(int id, String host, int port) throws Exception {
        this.id = id;
        this.schedulerAddress = InetAddress.getByName(host);
        this.schedulerPort = port;
        this.fsm = new DroneStateMachine(new DroneContext(id));
    }

    public void start() {
        try {
            socket = new DatagramSocket(); // ephemeral port
            System.out.println("[Drone " + id + "] started on local port " + socket.getLocalPort());

            while (true) {
                if (fsm.getState() == DroneStatus.IDLE) {
                    sendReady();
                    FireEvent event = waitForAssignment();

                    if (event == null) {
                        Thread.sleep(500);
                        continue;
                    }

                    fsm.handleEvent(DroneEvent.ASSIGNMENT_RECEIVED);
                    performMission(event);
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private FireEvent waitForAssignment() throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        String msg = new String(packet.getData(), 0, packet.getLength());

        if (msg.equals("NO_TASK")) {
            return null;
        }

        if (!msg.startsWith("ASSIGN,")) {
            return null;
        }

        String[] p = msg.split(",");
        String time = p[1];
        int zoneId = Integer.parseInt(p[2]);
        String type = p[3];
        String severity = p[4];
        int x = Integer.parseInt(p[5]);
        int y = Integer.parseInt(p[6]);

        FaultType faultType = (p.length >= 8) ? FaultType.fromString(p[7]) : FaultType.NONE; //Parse the fault type from the message

        return new FireEvent(time, zoneId, type, severity, x, y, faultType);
    }

    private void performMission(FireEvent event) throws Exception {
        int requiredAgent = litresFor(event.severity);

        while (requiredAgent > 0) {
            sendStatus(DroneStatus.TAKEOFF);
            Thread.sleep(500);
            fsm.handleEvent(DroneEvent.TAKEOFF_DONE);

            sendStatus(DroneStatus.EN_ROUTE);

            //STUCK_IN_FLIGHT fault injection
            if (event.faultType == FaultType.STUCK_IN_FLIGHT) { // If it has a stuck in flight fault, send a message to the scheduler and return
                send("DRONE_FAULT," + id + ",STUCK_IN_FLIGHT," + event.zoneId);
                return;
            }
            else moveTo(event.centerX, event.centerY); //If no stuck in flight fault continue as normal (move drone)

            //CORRUPTED_MESSAGE fault injection
            if (event.faultType == FaultType.CORRUPTED_MESSAGE) {
                send("#########"); // If event is corrupted, send a corrupted message and return
                return;
            }
            sendArrival(event.zoneId); //If message not corrupted continue as normal
            fsm.handleEvent(DroneEvent.ARRIVED_AT_ZONE);
            sendStatus(DroneStatus.EXTINGUISHING);

            //NOZZLE_FAULT fault injection
            if (event.faultType == FaultType.NOZZLE_FAULT) { // if it has a nozzle fault, send a message to scheduler and return
                send("DRONE_FAULT," + id + ",NOZZLE_FAULT," + event.zoneId);
                return;
            }
            while (requiredAgent > 0 && agent > 0) {
                agent--;
                requiredAgent--;
                sendStatus(DroneStatus.EXTINGUISHING);
                Thread.sleep(200);
            }

            if (requiredAgent == 0) {
                sendCompletion(event.zoneId);
                fsm.handleEvent(DroneEvent.FIRE_DONE);
            } else {
                fsm.handleEvent(DroneEvent.AGENT_EMPTY);
            }

            sendStatus(DroneStatus.RETURNING);
            moveTo(0, 0);
            fsm.handleEvent(DroneEvent.RETURNED_TO_BASE);

            agent = 14;
            sendStatus(DroneStatus.REFILLED);
            fsm.handleEvent(DroneEvent.REFILL_DONE);
        }
    }

    public DroneStatus getState() {
        return fsm.getState();
    }

    private int litresFor(String severity) {
        if (severity.equalsIgnoreCase("High")) return 30;
        if (severity.equalsIgnoreCase("Moderate")) return 20;
        return 10;
    }

    private void moveTo(int targetX, int targetY) throws Exception {
        int startX = droneX;
        int startY = droneY;
        int steps = 20;

        for (int i = 1; i <= steps; i++) {
            int x = startX + (targetX - startX) * i / steps;
            int y = startY + (targetY - startY) * i / steps;
            sendPosition(x, y);
            Thread.sleep(100);
        }
    }

    private void sendReady() throws Exception {
        send("READY," + id + "," + droneX + "," + droneY + "," + agent);
        System.out.println("[Drone " + id + "] READY");
    }

    private void sendStatus(DroneStatus status) throws Exception {
        send("DRONE_STATUS," + id + "," + status + "," + agent);
        System.out.println("[Drone " + id + "] Status: " + status + " Agent: " + agent);
    }

    private void sendPosition(int x, int y) throws Exception {
        droneX = x;
        droneY = y;
        send("DRONE_POS," + id + "," + x + "," + y);
    }

    private void sendArrival(int zoneId) throws Exception {
        send("DRONE_ARRIVED," + id + "," + zoneId);
        System.out.println("[Drone " + id + "] Arrived at zone: " + zoneId);
    }

    private void sendCompletion(int zoneId) throws Exception {
        send("DRONE_COMPLETE," + id + "," + zoneId);
        System.out.println("[Drone " + id + "] Completed zone: " + zoneId);
    }

    private void send(String msg) throws Exception {
        byte[] data = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, schedulerAddress, schedulerPort);
        socket.send(packet);
    }

    public static void main(String[] args) throws Exception {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        new DroneSubsystem(id, "localhost", 7000).start();
    }
}
