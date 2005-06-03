/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.util;

import javax.jms.JMSException;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class JBossJMSException extends JMSException
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public JBossJMSException(String reason) {
     this(reason, null, null);
   }

   public JBossJMSException(Exception cause) {
     this(null, null, cause);
   }

   public JBossJMSException(String reason, String errorCode) {
     this(reason, errorCode, null);
   }

   public JBossJMSException(String reason, Throwable cause)
   {
      this(reason, null, cause);
   }

   public JBossJMSException(String reason, String errorCode, Throwable cause)
   {
      super(reason, errorCode);
      if (cause instanceof Exception)
      {
         setLinkedException((Exception)cause);
      }
      else
      {
         setLinkedException(new Exception(cause));
      }
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------   
}
