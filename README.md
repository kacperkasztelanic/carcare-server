# CarCare server app
A websystem supporting vehicle fleet management tasks.  
Among other features it supports:
- tracking of repairs, services, inspections, insurances, refuels and mileage,
- e-mail notifications about important events, e.g. end of insurance policy,
- statistics and Excel reports concerning events, mileage, costs of the fleet maintenance.

The system consists of two separate apps - server and client - deployed in Docker containers behind a NGINX-based reverse proxy.   
On the server side it features SpringBoot along with Spring MVC, Spring Data, Spring Security, Spring AOP, Hibernate as an JPA implementation and MariaDB as a data store.  
The server app uses among others: Lombok, Vavr, Guava and Apache Libraries.  
On the client side there is a React app written in TypeScript with Redux store managing its state. 
