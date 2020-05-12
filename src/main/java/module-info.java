/**
 * @author Adam Brusselback.
 */
module com.gosimple.jpgagent {
    requires org.slf4j;
    requires java.sql;
    requires args4j;
    requires org.postgresql.jdbc;
    requires java.naming;
    requires java.mail;
    exports com.gosimple.jpgagent;
}