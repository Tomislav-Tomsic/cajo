package gnu.cajo.invoke;

import java.io.*;
import java.net.*;
import java.rmi.*;
import java.util.zip.*;
import java.rmi.server.*;
import java.rmi.registry.*;

/*
 * Generic Item Interface Exporter
 *
 * For issues or suggestions mailto:cajo@dev.java.net
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, at version 2.1 of the license, or any
 * later version.  The license differs from the GNU General Public License
 * (GPL) to allow this library to be used in proprietary applications. The
 * standard GPL would forbid this.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * To receive a copy of the GNU General Public License visit their website at
 * http://www.gnu.org or via snail mail at Free Software Foundation Inc.,
 * 59 Temple Place Suite 330, Boston MA 02111-1307 USA
 */

/**
 * This utility class takes a object, and allows it to be called from
 * remote VMs. This class eliminates the need to maintain multiple
 * specialized rmic interface compilations for interactions between various,
 * application specific objects. It also contains several useful utility
 * methods, to further support the invoke package paradigm. It can also be run
 * as an application, to load an object, and remote it within a VM.
 *
 * @version 1.0, 01-Nov-99 Initial release
 * @author John Catherino
 */
public final class Remote extends UnicastRemoteObject implements RemoteInvoke {
   private static final class RSSF implements RMIServerSocketFactory {
      private int port;
      private String host;
      public ServerSocket createServerSocket(int port) throws IOException {
         ServerSocket ss =
            new ServerSocket(this.port, 50, InetAddress.getByName(host));
         if (this.port == 0) {
            this.port = ss.getLocalPort();
            rcsf.port = this.port;
         } else if (rcsf.port == 0) rcsf.port = this.port;
         return ss;
      }
   }
   private static final class RCSF
      implements RMIClientSocketFactory, Serializable {
      private int port;
      private String host;
      public Socket createSocket(String host, int port) throws IOException {
         return new Socket(this.host, this.port != 0 ? this.port : port);
      }
   }
   private static Invoke proxy;
   private static Registry registry;
   /**
    * A global reference to the remote client socket factory.  This is the
    * factory local items will use to communicate with remote items. It simply
    * returns a standard {@link java.net.Socket socket}, bypassing all of the
    * http fallback mechanisms of the default
    * {@link java.rmi.server.RMIClientSocketFactory ClientSocketFactory}.
    */
   public static final RCSF rcsf = new RCSF();
   /**
    * A global reference to the remote client server factory.  This is the
    * factory the remote items use to receive requests from, and asynchronously
    * communicate with, other items. It simply returns a standard
    * {@link java.net.ServerSocket ServerSocket}, bypassing all of the http
    * fallback mechanisms of the default
    * {@link java.rmi.server.RMIServerSocketFactory ServerSocketFactory}.
    */
   public static final RSSF rssf = new RSSF();
   /**
    * This method is provided to obtain the server's host name.
    * This is useful when the host can have one of multiple addresses, either
    * because it has multiple network interface cards, or is multi-homed. 
    * @return The server address on which the item is remoted.
    */
   public static String getServerHost() { return rssf.host; }
   /**
    * This method is provided to obtain the local  server socket port
    * number. This can be particularly useful if the item was remoted on an
    * anonymous port.  If a firewall is in use, this inbound port must be made
    * accessible to outside clients.
    * @return The local ServerSocket port number on which the item is remoted.
    */
   public static int getServerPort() { return rssf.port; }
   /**
    * This method is provided to obtain the host name remote clients must
    * use to contact this server. This can be different from the local server
    * name or address for if NAT is being used.
    * @return The server address clients must use to connect to the
    * server.
    */
   public static String getClientHost() { return rcsf.host; }
   /**
    * This method is provided to obtain the socket port number the remote
    * client must use to contact the server.  This can be different from
    * the server port number if NAT port translation is being used.
    * @return The port clients must connect on to reach the server.
    */
   public static int getClientPort() { return rcsf.port; }
   /**
    * This method configures the class' TCP parameters for RMI.  It allows
    * complete specification of client-side and server-side ports and
    * hostnamess.  Is is used to override default values.  It is necessary
    * when the VM is either operating behind a firewall, is multi-addressed,
    * or is using NAT. <p><i>Note:</i> If the class is to be custom configured,
    * it must be done <b>before</b> any items are remoted. The first two
    * parameters control how the sockets will be configured locally, the
    * second two control how a remote object's sockets will be configured
    * to communicate with this object.<p>
    * @param serverHost The local domain name, or IP address of this host.
    * If null, it will use the default local address.  Typically it is
    * specified when the server has multiple phyisical network interfaces, or
    * is multi-homed, i.e. having multiple logical network interfaces.
    * @param serverPort Specifies the local port on which this object is
    * serving clients. It can be zero, to use an anonymous port.  If firewalls
    * are being used, it must be an accessible port, into this server. If this
    * port is zero, and the ClientPort argument is non-zero, then the
    * ClientPort value will automatically substituted.
    * @param clientHost The domain name, or IP address the remote client will
    * use to communicate with this server.  If null, it will be the same as
    * serverHost resolution.  This would need to be explicitly specified if
    * the object is operating behind NAT, that is, when the object's subnet IP
    * address is <i>not</i> the same as its address outside the subnet.
    * @param clientPort Specifies the particular port on which the client
    * will connect to the server.  Typically this is the <i>same</i> number
    * as the serverPort argument, but could be different, if port translation
    * is being used.  If the clientPort field is 0, i.e. anonymous, its port
    * value will be automatically assigned to match the server, even if the
    * server port is anonymous.
    * @throws java.net.UnknownHostException If the IP address or name of the
    * serverHost can not be resolved.
    */
   public static void config(String serverHost, int serverPort,
      String clientHost, int clientPort) throws java.net.UnknownHostException {
      rssf.host = serverHost != null ?
         serverHost : InetAddress.getLocalHost().getHostName();
      rcsf.host = clientHost != null ? clientHost : rssf.host;
      rssf.port =
         serverPort != 0 ? serverPort : clientPort != 0 ? clientPort : 0;
      rcsf.port = clientPort != 0 ? clientPort : rssf.port;
      try { // this won't work if running as an applet
         System.setProperty("java.rmi.server.useLocalHostname", "true");
         System.setProperty("java.rmi.server.hostname", rcsf.host);
      } catch(SecurityException x) {}
   }
   /**
    * A utility method to reconstitute a zipped marshalled object (zedmob)
    * into a remote item reference.
    * @param is The input stream containing the zedmob of the item reference.
    * @return A reconstituted reference to the item.
    * @throws IOException if the zedmob format is invalid.
    * @throws ClassNotFoundException if a proxy object was sent, and remote
    * class loading was not enabled in this VM.
    */
   public static Invoke zedmob(InputStream is)
      throws ClassNotFoundException, IOException {
      GZIPInputStream   gis = new GZIPInputStream(is);
      ObjectInputStream ois = new ObjectInputStream(gis);
      MarshalledObject  mob = (MarshalledObject)ois.readObject();
      ois.close();
      gis.close();
      return (Invoke)mob.get();
   }
   /**
    * A utility method to load either an item, or a zipped marshalled object
    * (zedmob) of an item, from a URL, file, or from a remote rmiregistry.
    * If the item is in a local file, it can be either inside the server's
    * jar file, or on its local file system.<p> Loading an item from a file
    * can be specified in one of three ways:<p><ul>
    * <li>As a URL; in the format file://path/name.
    * <li>As a class file; in the format path/name
    * <li>As a serialized item; in the format /path/name</ul><p>
    * File loading will first be attempted from within the server's jar file,
    * if that fails, it will then look in the local filesystem.<i>Note:</i> any
    * socket connections made by the incoming item cannot be known at compile
    * time, so proper operation if this VM is behind a firewall could be
    * blocked. Use behind a firewall would require knowing all the ports that
    * would be needed in advance, and enabling them before attempting the
    * loading.
    * @param url The URL where to get the object: file:// http:// ftp://
    * /path/name or path/name or //[host][:port]/[name]. The host, port,
    * and name, are all optional. If missing the host is presumed local, the
    * port 1099, and the name "main". The internal resource can be
    * returned as a MarshalledObject, it will be extracted automatically.
    * If the URL is null, it will be assumed to be ///.
    * @return A reference to the item contained in the URL. It may be either
    * local, or remote to this VM.
    * @throws RemoteException if the remote registry could not be reached.
    * @throws NotBoundException if the requested name is not in the registry.
    * @throws IOException if the zedmob format is invalid.
    * @throws ClassNotFoundException if a proxy was sent to the VM, and
    * proxy hosting was not enabled.
    * @throws InstantiationException when the URL specifies a class name
    * which cannot be instantiated at runtime.
    * @throws IllegalAccessException when the url specifies a class name
    * and it does not support a no-arg constructor.
    * @throws MalformedURLException if the URL is not in the format explained
    */
   public static Invoke getItem(String url) throws Exception {
      Invoke item = null;
      if (url == null) url = "///main";
      else if (url.startsWith("//") && url.endsWith("/")) url += "main";
      if (url.startsWith("//")) { // if from an rmiregistry
         item = (Invoke)java.rmi.Naming.lookup(url); // get reference
      } else if (url.startsWith("/")) { // if from a serialized object file
         InputStream ris = Remote.class.getResourceAsStream(url);
         if (ris == null) ris = new FileInputStream('.' + url);
         item = zedmob(ris);
         ris.close();
      } else if (url.indexOf(':') == -1) { // from a class file
         item = (Invoke)Class.forName(url).newInstance();
      } else { // otherwise from a real URL, http:// ftp:// file:// etc.
         InputStream uis = new URL(url).openStream();
         item = zedmob(uis);
         uis.close();
      }
      return item;
   }
   private final Object item;
   /**
    * The constructor takes an object, and allows it to be remotely invoked.
    * If the object implements the {@link Invoke Invoke} interface, (i.e. it
    * is an 'item') it will simply route all remote invocations to it.
    * Otherwise it will use Java reflection to attempt to invoke the remote
    * calls directly on the object.
    * @param  item The object to make remotely callable.  It may be an
    * arbitrary object of any type, or an item (either local or remote).
    * @throws RemoteExcepiton If the remote instance could not be be created.
    */
   public Remote(Object item) throws RemoteException {
      super(rssf.port, rcsf, rssf);
      this.item = item;
   }
   /**
    * This method checks if two wrappers holding an equavilent inner item.
    * It short-circuit's the invocation, returning the result of the internal
    * item's equals invocation.
    * @param object A reference to another object to compare for equality.
    * @return The result of the equals method called on the internal item. 
    */
   public boolean equals(Object object) { return item.equals(object); }
   /**
    * This method is used to identify the internal item.  It short-circuit's
    * the invocation, returning the result of the internal item's toString
    * invocation.
    * @return The result of the toString method called on the internal item. 
    */
   public String toString() { return item.toString(); }
   /**
    * This method is used to identify the internal item.  It short-circuit's
    * the invocation, returning the result of the internal item's hashCode
    * invocation.
    * @return The semi-unique integer identifier for the internal object.
    */
   public int hashCode() { return item.hashCode(); }
   /**
    * The sole generic, multi-purpose interface for communication between VMs.
    * This function may be called reentrantly, so the inner item <i>must</i>
    * synchronize its critical sections as necessary.  If the internal item
    * implements the {@link Invoke Invoke} interface, the call will simply be
    * forwarded to the internal implementation's method.  Otherwise, the
    * method specified will be invoked, with the provided arguments if any,
    * on the internal object via the Java reflection mechanism.
    * <p><i>Note:</i> reflection will only invoke a method whose argument
    * types match exactly.  Unfortunately polymorphism is not recognized.
    * Therefore as one <b>special case</b>, all arguments implementing the
    * Invoke interface, will be forcibly cast as being Invoke type. Application
    * specific methods must therefore declare the argument type as Invoke, but
    * could easily test its type at runtime if it mattered. In general, this is
    * a recommended practice for this paradigm, to reinforce local/remote
    * transparency.
    * @param  method The method to invoke on the internal item.
    * @param args The arguments to provide to the method for its invocation.
    * It can be a single object, an array of objects, or even null.
    * @return The sychronous data, if any, resulting from the invocation.
    * @throws java.rmi.RemoteException For network communication related
    * reasons.
    * @throws IllegalArgumentException If reflection is going to be used,
    * and the method argument is null.
    * @throws NoSuchMethodException If no matching method can be found.
    * @throws Exception If the internal object rejects the request, for any
    * application specific reason.
    */
   public Object invoke(String method, Object args) throws Exception {
      if (item instanceof Invoke) return ((Invoke)item).invoke(method, args);
      if (method == null)
         throw new IllegalArgumentException("Method argument cannot be null");
      Class types[] = null;
      if (args instanceof Object[]) {
         types = new Class[((Object[])args).length];
         for (int i = 0; i < types.length; i++)
            types[i] = ((Object[])args)[i] instanceof Invoke ?
               Invoke.class : ((Object[])args)[i].getClass();
      } else if (args != null) {
         types = new Class[] {
            args instanceof Invoke ? Invoke.class : args.getClass() };
         args = new Object[] { args };
      }
      return item.getClass().getMethod(method, types).invoke(item, (Object[])args);
   }
   /**
    * This method sends its remote reference to another item, either from a
    * URL, file, or from a remote rmiregistry. It will invoke the local
    * {@link #getItem getItem} method to obtain a reference to the remote
    * item. It will next invoke the received reference's invoke method with
    * a "send" value, and a reference to itself as its sole argument.
    * @param url The URL where to get the remote host interface: file://
    * http:// ftp:// /path/name or path/name or //[host][:port]/[name].
    * The host, port, and name, are all optional. If missing the host is
    * presumed local, the port 1099, and the name "main".  If the URL is
    * null, it will be assumed to be ///.
    * @return Whatever the item returns in receipt of the reference,
    * even null.
    * @throws Exception Either from the getItem invocation, or if the
    * item reference invocation fails.
    */    
   public Object send(String url) throws Exception {
      if (url == null) url = "///main";
      else if (url.startsWith("//") && url.endsWith("/")) url += "main";
      return getItem(url).invoke("send", this);
   }
   /**
    * This method will write the remote item reference to an output stream
    * as a zipped marshalled object (zedmob). Zedmob is the standard serialized
    * format for an item, or remote reference to an item, in this paradigm.
    * This can be used to 'freeze-dry' the remote reference, to a file for
    * later use, send it over the network, or to an object archival service,
    * for example.
    * @param os The output stream on which to write the reference.  It may be
    * a file stream, a socket stream, or any other type of stream.
    * @throws IOException For any stream related writing error.
    */
   public void zedmob(OutputStream os) throws IOException {
      GZIPOutputStream   zos = new GZIPOutputStream(os);
      ObjectOutputStream oos = new ObjectOutputStream(zos);
      oos.writeObject(new MarshalledObject(this));
      oos.flush();
      zos.flush();
      oos.close();
      zos.close();
   }
   /**
    * The application method loads a zipped marshalled object (zedmob) from a
    * URL, or a file, and allows it run in this virtual machine. It uses
    * the {@link #getItem getItem} method to load the item.  Following loading
    * of the item, it will also create an rmiregistry, and bind a remote
    * reference to it under the name "main".  This will also allow remote
    * clients to connect to, and interact with it.  <i>Note:</i>It will
    * install a {@link NoSecurityManager NoSecurityManager}, which if not
    * blocked by a user-specified SecurityManager, will allow the* loaded item,
    * and all loaded proxies after starting, <b>full permissions</b> on this
    * machine.
    * @param args[0] The optional URL where to get the object: file:// http://
    * ftp:// ..., /path/name <serialized>, path/name <class>, or alternatively;
    * //[host][:port]/[name].  If no arguments are provided, the URL will be
    * assumed to be //localhost:1099/main.
    * @param args[1] The optional external client host name, if using NAT.
    * @param args[2] The optional external client port number, if using NAT.
    * @param args[3] The optional internal client host name, if multi home/NIC.
    * @param args[4] The optional internal client port number, if using NAT.
    * @param args[5] The optional URL where to get a proxy item: file://
    * http:// ftp:// ..., //host:port/name (rmiregistry), /path/name
    * (serialized), or path/name (class).  It will be passed into the loaded
    * proxy as the sole argument to a setItem method invoked on the loaded item.
    */
   public static void main(String args[]) {
      try {
         if (args.length == 0) args = new String[] { "///main" };
         String clientHost = args.length > 1 ? args[1] : null;
         int clientPort    = args.length > 2 ? Integer.parseInt(args[2]) : 0;
         String localHost  = args.length > 3 ? args[3] : null;
         int localPort     = args.length > 4 ? Integer.parseInt(args[4]) : 0;
         config(localHost, localPort, clientHost, clientPort);
         try {
            System.setSecurityManager(new NoSecurityManager());
            System.setProperty("java.rmi.server.disableHttp", "true");
         } catch(SecurityException x) {}
         proxy = getItem(args[0]);
         if (args.length > 5) proxy.invoke("setItem", getItem(args[5]));
         registry =
            LocateRegistry.createRegistry(getServerPort(), rcsf, rssf);
         registry.bind("main", new Remote(proxy));
      } catch (Exception x) { x.printStackTrace(System.err); }
   }
}
