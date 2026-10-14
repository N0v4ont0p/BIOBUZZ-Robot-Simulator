package org.biobuzz.sim.rc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

// A Robot Controller configuration, stored the way the real one is: /sdcard/FIRST/<name>.xml with
// <Robot><LynxUsbDevice><LynxModule> ... device elements tagged with the device type's XML tag.
public final class RobotConfig {
    public static final class Device {
        public String type, name;
        public int port, bus = -1;

        public Device(String type, String name, int port, int bus) {
            this.type = type;
            this.name = name;
            this.port = port;
            this.bus = bus;
        }
    }

    public static final class Module {
        public String name;
        public int address;
        public final List<Device> motors = new ArrayList<>(), servos = new ArrayList<>(), i2c = new ArrayList<>(), digital = new ArrayList<>();

        public Module(String name, int address) {
            this.name = name;
            this.address = address;
        }
    }

    public final String name;
    public final List<Module> modules = new ArrayList<>();

    public RobotConfig(String name) {
        this.name = name;
    }

    public Module module(int address) {
        for (Module m : modules) if (m.address == address) return m;
        return null;
    }

    public static RobotConfig read(File f, java.util.Set<String> i2cTags, java.util.Set<String> servoTags, java.util.Set<String> digitalTags) throws Exception {
        String n = f.getName().replaceAll("\\.xml$", "");
        RobotConfig c = new RobotConfig(n);
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        NodeList mods = d.getElementsByTagName("LynxModule");
        for (int i = 0; i < mods.getLength(); i++) {
            Element me = (Element) mods.item(i);
            Module m = new Module(me.getAttribute("name"), Integer.parseInt(me.getAttribute("port")));
            NodeList kids = me.getChildNodes();
            for (int k = 0; k < kids.getLength(); k++) {
                Node node = kids.item(k);
                if (!(node instanceof Element)) continue;
                Element e = (Element) node;
                String tag = e.getTagName();
                int port = Integer.parseInt(e.getAttribute("port"));
                Device dev = new Device(tag, e.getAttribute("name"), port, e.hasAttribute("bus") ? Integer.parseInt(e.getAttribute("bus")) : -1);
                if (dev.bus >= 0 || i2cTags.contains(tag)) m.i2c.add(dev);
                else if (servoTags.contains(tag)) m.servos.add(dev);
                else if (digitalTags.contains(tag)) m.digital.add(dev);
                else m.motors.add(dev);
            }
            c.modules.add(m);
        }
        return c;
    }

    public void write(File f) throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element robot = d.createElement("Robot");
        robot.setAttribute("type", "FirstInspires-FTC");
        d.appendChild(robot);
        Element usb = d.createElement("LynxUsbDevice");
        usb.setAttribute("name", "Control Hub Portal");
        usb.setAttribute("serialNumber", "(embedded)");
        usb.setAttribute("parentModuleAddress", "173");
        robot.appendChild(usb);
        for (Module m : modules) {
            Element me = d.createElement("LynxModule");
            me.setAttribute("name", m.name);
            me.setAttribute("port", Integer.toString(m.address));
            for (List<Device> list : java.util.Arrays.asList(m.motors, m.servos, m.i2c, m.digital)) {
                for (Device dev : list) {
                    Element e = d.createElement(dev.type);
                    e.setAttribute("name", dev.name);
                    e.setAttribute("port", Integer.toString(dev.port));
                    if (dev.bus >= 0) e.setAttribute("bus", Integer.toString(dev.bus));
                    me.appendChild(e);
                }
            }
            usb.appendChild(me);
        }
        f.getParentFile().mkdirs();
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.STANDALONE, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(new DOMSource(d), new StreamResult(f));
    }
}
