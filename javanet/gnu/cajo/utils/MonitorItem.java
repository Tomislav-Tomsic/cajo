package gnu.cajo.utils;

import gnu.cajo.invoke.Invoke;
import gnu.cajo.invoke.Remote;
import java.rmi.server.RemoteServer;
import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import java.util.Date;
import java.text.DateFormat;
import java.io.PrintStream;
import java.io.OutputStream;

/*
 * Item Invocation Monitor
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
 * This class is used to instrument an item for invocation logging
 * purposes. It is intended as a replacement for standard RMI logging, in that
 * this logger is aware of the Invoke package methodology, and can decode it
 * properly.  Specifically, it will gather information about the calling
 * client, the method called, the inbound and outbound data. It will also
 * record the approximate time between client invocations, the time used to
 * service the invocation, and the approximate percentage of free memory
 * available at the completion of the operation.  Subclassing is allowed,
 * primarily to create self-monitoring classes, not to change the data
 * being monitored.
 * <p><i>Note:</i> monitoring an item can be expensive in runtime efficiency.
 * It is best used for debug and performance analysis, during development, or
 * in production for items that would not be called highly frequently.
 *
 * @version 1.0, 01-Nov-99 Initial release
 * @author John Catherino
 */
public final class MonitorItem implements Invoke {
   private final Object item;
   private final PrintStream ps;
   private long oldtime;
   /**
    * This creates the monitor object, to instrument the target item's use.
    * The the logging information is passed to the OutputStream, where it can
    * be logged to a file, or simply sent to the console.
    * @param item The item to receive the client invocation.
    * @param os The OutputStream to send the formatted log information.  It
    * could be a file or socket stream, or even System.out.
    */
   public MonitorItem(Object item, OutputStream os) {
      this.item = item;
      this.ps = os instanceof PrintStream ?
         (PrintStream)os : new PrintStream(os);
      oldtime = System.currentTimeMillis();
   }
   /**
    * This method is overridden here to ensure that two different monitor
    * items holding the target item return the same value.
    * @return The hash code returned by the target item
    */
   public int hashCode() { return item.hashCode(); }
   /**
    * This method is overridden here to ensure that two different monitor
    * items holding the same target item return true.
    * @param obj An object, presumably another item, to compare
    * @return True if the inner items are equivalent, otherwise false
    */
   public boolean equals(Object obj) { return item.equals(obj); }
   /**
    * This method is overridden here to provide the name of the internal
    * object, rather than the name of this object.
    * @return The string returned by the internal item's toString method.
    */
   public String toString() { return item.toString(); }
   /**
    * This method logs the incoming calls, passing the caller's data to the
    * internal item. It records the following information:<ul>
    * <li> The name of the item being called
    * <li> The host address of the caller (or localhost)
    * <li> The method the caller is invoking
    * <li> The data the caller is sending
    * <li> The data resulting from the invocation, or the Exception
    * <li> The idle time between invocations, in milliseconds.
    * <li> The run time of the invocation time, in milliseconds
    * <li> The free memory percentage, following the invocation</ul>
    * @param  data The to pass to the internal data item
    * @return The sychronous data, if any, resulting from the invocation
    * @throws RemoteException For a network related failure.
    * @throws Exception If the internal item rejects the request
    */
   public Object invoke(String method, Object args) throws Exception {
      long time = System.currentTimeMillis();
      Object arg = args;
      try {
         if (item instanceof Invoke)
            return args = ((Invoke)item).invoke(method, args);
         else if (method == null)
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
         return args = item.getClass().getMethod(method, types).
            invoke(item, (Object[])args);
      } catch(Exception x) {
         args = x;
         throw x;
      } finally {
         try {
            int run = (int)(System.currentTimeMillis() - time);
            synchronized(ps) {
               ps.print("\nCaller host =\t");
               try { ps.print(RemoteServer.getClientHost()); }
               catch(ServerNotActiveException x) { ps.print("localhost"); }
               ps.print("\nItem called =\t");
               ps.print(item.toString());
               ps.print("\nMethod call =\t");
               ps.print(method);
               ps.print("\nMethod args =\t");
               if (arg instanceof Object[]) {
                  ps.print("<array>");
                  for (int i = 0; i < ((Object[])arg).length; i++) {
                     ps.print("\n\t[");
                     ps.print(i);
                     ps.print("] =\t");
                     ps.print(((Object[])arg)[i].toString());
                  }
               } else ps.print(arg != null ? arg.toString() : "null");
               ps.print("\nResult data =\t");
               if (args instanceof Exception) {
                  ((Exception)args).printStackTrace(ps);
               } else if (args instanceof Object[]) {
                  ps.print("array");
                  for (int i = 0; i < ((Object[])args).length; i++) {
                     ps.print("\n\t[");
                     ps.print(i);
                     ps.print("] =\t");
                     ps.print(((Object[])args)[i].toString());
                  }
               } else ps.print(args != null ? args.toString() : "null");
               ps.print("\nIdle time   =\t");
               ps.print(time - oldtime);
               ps.print(" ms");
               ps.print("\nBusy time   =\t");
               ps.print(run);
               ps.print(" ms");
               Runtime rt = Runtime.getRuntime();
               ps.print("\nFree memory =\t");
               ps.print((int)((rt.freeMemory() * 100) / rt.totalMemory()));
               ps.println('%');
               oldtime = time;
            }
         } catch(Exception x) { x.printStackTrace(System.err); }
      }
   }
}
