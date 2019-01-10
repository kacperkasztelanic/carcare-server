-- MySQL dump 10.16  Distrib 10.3.9-MariaDB, for Win64 (AMD64)
--
-- Host: 192.168.99.100    Database: carcare
-- ------------------------------------------------------
-- Server version	10.3.10-MariaDB-1:10.3.10+maria~bionic

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `DATABASECHANGELOG`
--

DROP TABLE IF EXISTS `DATABASECHANGELOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOG` (
  `ID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int(11) NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `DATABASECHANGELOG`
--

LOCK TABLES `DATABASECHANGELOG` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOG` DISABLE KEYS */;
INSERT INTO `DATABASECHANGELOG` VALUES ('00000000000001','jhipster','config/liquibase/changelog/00000000000000_initial_schema.xml','2019-01-10 20:13:20',1,'EXECUTED','7:4afb46bcf498cc2fd904f4b91e294183','createTable tableName=jhi_user; createTable tableName=jhi_authority; createTable tableName=jhi_user_authority; addPrimaryKey tableName=jhi_user_authority; addForeignKeyConstraint baseTableName=jhi_user_authority, constraintName=fk_authority_name, ...','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-1','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',2,'EXECUTED','7:0e71d48bd5c1a2d0a8c558849905ab92','createTable tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-2','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',3,'EXECUTED','7:c588cb77afd71212890df5bac640ddbb','createTable tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-3','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',4,'EXECUTED','7:2ecbf8c8b7906544292d056b1f36cf0f','createTable tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-4','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',5,'EXECUTED','7:fa643e65ab8b84ca6f60ff09d4ffc720','createTable tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-5','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',6,'EXECUTED','7:8f2710ce3ed6393e3059d8f88cca619f','createTable tableName=refuels','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-6','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',7,'EXECUTED','7:55cd432dfaf71968dcd860251d68e025','createTable tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-7','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',8,'EXECUTED','7:a05e831ab2580bb8849cb2d6b410abf9','createTable tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-8','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',9,'EXECUTED','7:8977f1ca3eeef333a8e857eece10b1af','createTable tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-9','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',10,'EXECUTED','7:6dd2437b98a11c699d257afaae1466d5','addUniqueConstraint constraintName=UC_FUEL_TYPESTYPE_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-10','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',11,'EXECUTED','7:88ea0d984fa8ff105fca78a0f0d57eeb','addUniqueConstraint constraintName=UC_INSPECTIONSUUID_COL, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-11','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',12,'EXECUTED','7:f224ca56194224a6984d694d2156119a','addUniqueConstraint constraintName=UC_INSURANCESUUID_COL, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-12','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',13,'EXECUTED','7:8f1c3e288280bc6e78cbc8e0b8814395','addUniqueConstraint constraintName=UC_INSURANCE_TYPESTYPE_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-13','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:20',14,'EXECUTED','7:e29e09392a0e71142ab1b7626c6dcd80','addUniqueConstraint constraintName=UC_REFUELSUUID_COL, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-14','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',15,'EXECUTED','7:3f91951577520638364b7995b445c30e','addUniqueConstraint constraintName=UC_REPAIRSUUID_COL, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-15','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',16,'EXECUTED','7:c0dbeb534b4307adcfe500624644706d','addUniqueConstraint constraintName=UC_ROUTINE_SERVICESUUID_COL, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-16','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',17,'EXECUTED','7:5da87e5df11fd3a30bbd2d1c5ee851a1','addUniqueConstraint constraintName=UC_VEHICLESUUID_COL, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-17','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',18,'EXECUTED','7:ffbf46241d987fb85d2c5de6a40015f8','addForeignKeyConstraint baseTableName=insurances, constraintName=FK24ys5lgfug2q78d310u04tylf, referencedTableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-18','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',19,'EXECUTED','7:9ac0a3097cebe9b60ea99c3b462f8df2','addForeignKeyConstraint baseTableName=refuels, constraintName=FK7cs5c7iw40lj73yo6s77u7rvl, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-19','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',20,'EXECUTED','7:2698a528e9e36f15fccb9a8dc8752dbd','addForeignKeyConstraint baseTableName=vehicles, constraintName=FKck94koff5phplxnts3lahjinu, referencedTableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-20','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',21,'EXECUTED','7:e8e9d186a4cabf0e7a9539a84511beee','addForeignKeyConstraint baseTableName=vehicles, constraintName=FKhm05kh6d8f082pgddom1q1yco, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-21','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',22,'EXECUTED','7:61e53ba053e945e0f9110f3378ba6392','addForeignKeyConstraint baseTableName=insurances, constraintName=FKk7a7uqrkf4cuvn4w2rsdymafk, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-22','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',23,'EXECUTED','7:46a91b16a3d957a37a8a5eb828fed957','addForeignKeyConstraint baseTableName=inspections, constraintName=FKlfc8sgfw636xmcre6gj9ra4pe, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-23','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',24,'EXECUTED','7:de3d91fa0fca4a49ec45b412e389f2e3','addForeignKeyConstraint baseTableName=repairs, constraintName=FKr8rwhlbv43kxbn4j93hkul7ax, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-24','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',25,'EXECUTED','7:58fe241f8a1fcb322de5488faa5b128a','addForeignKeyConstraint baseTableName=routine_services, constraintName=FKsy0jrbbtf29lpv37ahmtlj3dv, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-25','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',26,'EXECUTED','7:4fbb584535e451702de5411972a56530','dropDefaultValue columnName=activation_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-26','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:21',27,'EXECUTED','7:8cc1d59098fc2e24219908855713317c','dropDefaultValue columnName=email, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-27','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',28,'EXECUTED','7:de9c1cfe5b771e385742658192bf10a3','dropDefaultValue columnName=event_type, tableName=jhi_persistent_audit_event','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-28','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',29,'EXECUTED','7:4890df99882170149a47f36c80f4ce38','dropDefaultValue columnName=first_name, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-29','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',30,'EXECUTED','7:75319e33742eeef13b01c6c156425275','dropDefaultValue columnName=image_url, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-30','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',31,'EXECUTED','7:83e98119c120277c7e17fe3470a8bf8e','dropDefaultValue columnName=lang_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-31','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',32,'EXECUTED','7:323f3047873d9c5b284de40b5881bb89','dropDefaultValue columnName=last_modified_by, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-32','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',33,'EXECUTED','7:aa0afacab9b39e122c2605ee39dddbb4','dropDefaultValue columnName=last_name, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-33','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',34,'EXECUTED','7:13e9c7c3cf6fa9168cf0d109def4409f','dropDefaultValue columnName=reset_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545061695176-34','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-10 20:13:22',35,'EXECUTED','7:4d2c9e85eeecef4b597611537c9fad13','dropDefaultValue columnName=value, tableName=jhi_persistent_audit_evt_data','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-1','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',36,'EXECUTED','7:800ef3269aa70d9b1394dd93a39f8370','dropUniqueConstraint constraintName=UC_INSPECTIONSUUID_COL, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-2','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',37,'EXECUTED','7:9ac30e74165c99a233a8d7446a004aac','dropUniqueConstraint constraintName=UC_INSURANCESUUID_COL, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-3','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',38,'EXECUTED','7:f883641bc8db829dba3932665e63e1ac','dropUniqueConstraint constraintName=UC_REFUELSUUID_COL, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-4','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',39,'EXECUTED','7:8ef3059ac7d8d2370c69d3d4e91dd9a1','dropUniqueConstraint constraintName=UC_REPAIRSUUID_COL, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-5','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',40,'EXECUTED','7:e093094a000bdd8e771c3e616882fb1e','dropUniqueConstraint constraintName=UC_ROUTINE_SERVICESUUID_COL, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-6','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',41,'EXECUTED','7:5811915c21d3e79c189093e79fc526ae','dropUniqueConstraint constraintName=UC_VEHICLESUUID_COL, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-7','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',42,'EXECUTED','7:b5a8dfcb7f6b3eaffa40fcf433f36b0a','dropColumn columnName=uuid, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-8','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',43,'EXECUTED','7:7d3082e313a027b72a917233fb9bf09b','dropColumn columnName=uuid, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-9','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',44,'EXECUTED','7:6e16283710b8a5cbf6275ed0eaf9b04b','dropColumn columnName=uuid, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-10','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',45,'EXECUTED','7:243caaf0e0020f5eea16e319dcc94d9f','dropColumn columnName=uuid, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-11','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',46,'EXECUTED','7:40193f54468710b224d31b12cc867ebc','dropColumn columnName=uuid, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-12','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',47,'EXECUTED','7:f5d7a123009cfdf5233c255eb26b1171','dropColumn columnName=uuid, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-13','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',48,'EXECUTED','7:ee35373fea60bedaa7b6011c224c02bf','dropDefaultValue columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-14','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',49,'EXECUTED','7:06d91f24ac30f159188b9bd2acc4970d','dropDefaultValue columnName=image_content_type, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-15','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',50,'EXECUTED','7:d40ffe232dc7572a6f1f793ebd7ec306','dropDefaultValue columnName=insurer, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-16','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',51,'EXECUTED','7:2adef877c118340ec0e042351764de29','dropDefaultValue columnName=model_suffix, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-17','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',52,'EXECUTED','7:c56465ab52221a5ed0632be3211ed928','dropDefaultValue columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-18','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',53,'EXECUTED','7:0fff00b8125ed48ce215425546c10252','dropDefaultValue columnName=number, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-19','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',54,'EXECUTED','7:a3520b953370a6afd8f0dcd5b381e7fa','dropDefaultValue columnName=registration_certificate, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-20','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',55,'EXECUTED','7:73aa398d514e473e3e6b9a45a7c15d01','dropDefaultValue columnName=vehicle_card, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1545416098210-21','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-10 20:13:22',56,'EXECUTED','7:cd780528436784debfb10ed188458917','dropDefaultValue columnName=vin_number, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-1','Kacper (generated)','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:22',57,'EXECUTED','7:8e3a7f149cd12b08ad51db5976b5ca14','addNotNullConstraint columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-2','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:23',58,'EXECUTED','7:ce27a66a346667c5f06ed28afad11373','modifyDataType columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-3','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:23',59,'EXECUTED','7:63f5b0fbeb68a5ce35bce3267b0875db','modifyDataType columnName=details, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-4','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:23',60,'EXECUTED','7:657e87913e3cd37ec871935d471716d2','modifyDataType columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-5','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:23',61,'EXECUTED','7:a3b4d1d4c402584c608f8f0cbc22f89b','modifyDataType columnName=details, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546467663093-6','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-10 20:13:23',62,'EXECUTED','7:e696d62b3a32e60f35f9d73ac6e2df20','modifyDataType columnName=details, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546547935872-1','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-10 20:13:23',63,'EXECUTED','7:4f6a8f2f56680c0ec38cafb091a349c7','addNotNullConstraint columnName=details, tableName=inspections; dropDefaultValue columnName=details, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546547935872-2','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-10 20:13:23',64,'EXECUTED','7:ba30ecff44894234eb28c3ab9c543219','addNotNullConstraint columnName=details, tableName=insurances; dropDefaultValue columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546547935872-3','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-10 20:13:23',65,'EXECUTED','7:4500dcf9402efc83380612e36dd954b1','addNotNullConstraint columnName=details, tableName=repairs; dropDefaultValue columnName=details, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546547935872-4','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-10 20:13:23',66,'EXECUTED','7:1265ba7ec438672dbd8991f0f2e42f1d','addNotNullConstraint columnName=details, tableName=routine_services; dropDefaultValue columnName=details, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546547935872-5','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-10 20:13:23',67,'EXECUTED','7:e7fc087dbfde31990697a505a2a9a0ee','dropDefaultValue columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546548430063-1','Kacper (generated)','config/liquibase/changelog/20190103204656_changelog.xml','2019-01-10 20:13:23',68,'EXECUTED','7:daa93446a5a1b258aa76dd3a45509bfb','dropColumn columnName=image_content_type, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-1','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:23',69,'EXECUTED','7:bc8af992875eb1858a99f080f61aeb05','createTable tableName=reminder_advances','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-2','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:23',70,'EXECUTED','7:8cfcd06aa114ab5128bc66c4c791acad','addColumn tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-3','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:23',71,'EXECUTED','7:48bd13e2b8fd8a9680a36a20380bc0d9','addColumn tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-4','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:23',72,'EXECUTED','7:2aaae785d89367e5ba056cce61a1dfe4','addColumn tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-5','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:23',73,'EXECUTED','7:2fd9e8fb62848ee538323c88b8d27e4e','addColumn tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-6','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:24',74,'EXECUTED','7:514d91695bc49200c3f049d0481037fb','addUniqueConstraint constraintName=UC_FUEL_TYPESENGLISH_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-7','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:24',75,'EXECUTED','7:7ff8f3b2cca73bc052b9694ba2c0cb19','addUniqueConstraint constraintName=UC_FUEL_TYPESPOLISH_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-8','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:24',76,'EXECUTED','7:2c8cf603670d2a69f5486a31bc989645','addUniqueConstraint constraintName=UC_INSURANCE_TYPESENGLISH_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-9','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:24',77,'EXECUTED','7:9e8382d58b2afef514e4381dacead342','addUniqueConstraint constraintName=UC_INSURANCE_TYPESPOLISH_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'7151196974'),('1546875049766-10','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-10 20:13:24',78,'EXECUTED','7:11909b25ad79c4ffbda5faf06ac6c57b','addUniqueConstraint constraintName=UC_REMINDER_ADVANCESTYPE_COL, tableName=reminder_advances','',NULL,'3.5.4',NULL,NULL,'7151196974');
/*!40000 ALTER TABLE `DATABASECHANGELOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `DATABASECHANGELOGLOCK`
--

DROP TABLE IF EXISTS `DATABASECHANGELOGLOCK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOGLOCK` (
  `ID` int(11) NOT NULL,
  `LOCKED` bit(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `DATABASECHANGELOGLOCK`
--

LOCK TABLES `DATABASECHANGELOGLOCK` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` DISABLE KEYS */;
INSERT INTO `DATABASECHANGELOGLOCK` VALUES (1,'\0',NULL,NULL);
/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `databasechangelog`
--

DROP TABLE IF EXISTS `databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `databasechangelog` (
  `ID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int(11) NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `databasechangelog`
--

LOCK TABLES `databasechangelog` WRITE;
/*!40000 ALTER TABLE `databasechangelog` DISABLE KEYS */;
INSERT INTO `databasechangelog` VALUES ('00000000000001','jhipster','config/liquibase/changelog/00000000000000_initial_schema.xml','2019-01-09 00:13:48',1,'EXECUTED','7:4afb46bcf498cc2fd904f4b91e294183','createTable tableName=jhi_user; createTable tableName=jhi_authority; createTable tableName=jhi_user_authority; addPrimaryKey tableName=jhi_user_authority; addForeignKeyConstraint baseTableName=jhi_user_authority, constraintName=fk_authority_name, ...','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-1','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',2,'EXECUTED','7:0e71d48bd5c1a2d0a8c558849905ab92','createTable tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-2','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',3,'EXECUTED','7:c588cb77afd71212890df5bac640ddbb','createTable tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-3','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',4,'EXECUTED','7:2ecbf8c8b7906544292d056b1f36cf0f','createTable tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-4','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',5,'EXECUTED','7:fa643e65ab8b84ca6f60ff09d4ffc720','createTable tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-5','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',6,'EXECUTED','7:8f2710ce3ed6393e3059d8f88cca619f','createTable tableName=refuels','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-6','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',7,'EXECUTED','7:55cd432dfaf71968dcd860251d68e025','createTable tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-7','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',8,'EXECUTED','7:a05e831ab2580bb8849cb2d6b410abf9','createTable tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-8','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',9,'EXECUTED','7:8977f1ca3eeef333a8e857eece10b1af','createTable tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-9','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',10,'EXECUTED','7:6dd2437b98a11c699d257afaae1466d5','addUniqueConstraint constraintName=UC_FUEL_TYPESTYPE_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-10','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',11,'EXECUTED','7:88ea0d984fa8ff105fca78a0f0d57eeb','addUniqueConstraint constraintName=UC_INSPECTIONSUUID_COL, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-11','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',12,'EXECUTED','7:f224ca56194224a6984d694d2156119a','addUniqueConstraint constraintName=UC_INSURANCESUUID_COL, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-12','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',13,'EXECUTED','7:8f1c3e288280bc6e78cbc8e0b8814395','addUniqueConstraint constraintName=UC_INSURANCE_TYPESTYPE_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-13','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',14,'EXECUTED','7:e29e09392a0e71142ab1b7626c6dcd80','addUniqueConstraint constraintName=UC_REFUELSUUID_COL, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-14','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',15,'EXECUTED','7:3f91951577520638364b7995b445c30e','addUniqueConstraint constraintName=UC_REPAIRSUUID_COL, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-15','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',16,'EXECUTED','7:c0dbeb534b4307adcfe500624644706d','addUniqueConstraint constraintName=UC_ROUTINE_SERVICESUUID_COL, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-16','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',17,'EXECUTED','7:5da87e5df11fd3a30bbd2d1c5ee851a1','addUniqueConstraint constraintName=UC_VEHICLESUUID_COL, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-17','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',18,'EXECUTED','7:ffbf46241d987fb85d2c5de6a40015f8','addForeignKeyConstraint baseTableName=insurances, constraintName=FK24ys5lgfug2q78d310u04tylf, referencedTableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-18','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:48',19,'EXECUTED','7:9ac0a3097cebe9b60ea99c3b462f8df2','addForeignKeyConstraint baseTableName=refuels, constraintName=FK7cs5c7iw40lj73yo6s77u7rvl, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-19','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',20,'EXECUTED','7:2698a528e9e36f15fccb9a8dc8752dbd','addForeignKeyConstraint baseTableName=vehicles, constraintName=FKck94koff5phplxnts3lahjinu, referencedTableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-20','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',21,'EXECUTED','7:e8e9d186a4cabf0e7a9539a84511beee','addForeignKeyConstraint baseTableName=vehicles, constraintName=FKhm05kh6d8f082pgddom1q1yco, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-21','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',22,'EXECUTED','7:61e53ba053e945e0f9110f3378ba6392','addForeignKeyConstraint baseTableName=insurances, constraintName=FKk7a7uqrkf4cuvn4w2rsdymafk, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-22','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',23,'EXECUTED','7:46a91b16a3d957a37a8a5eb828fed957','addForeignKeyConstraint baseTableName=inspections, constraintName=FKlfc8sgfw636xmcre6gj9ra4pe, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-23','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',24,'EXECUTED','7:de3d91fa0fca4a49ec45b412e389f2e3','addForeignKeyConstraint baseTableName=repairs, constraintName=FKr8rwhlbv43kxbn4j93hkul7ax, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-24','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',25,'EXECUTED','7:58fe241f8a1fcb322de5488faa5b128a','addForeignKeyConstraint baseTableName=routine_services, constraintName=FKsy0jrbbtf29lpv37ahmtlj3dv, referencedTableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-25','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',26,'EXECUTED','7:4fbb584535e451702de5411972a56530','dropDefaultValue columnName=activation_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-26','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',27,'EXECUTED','7:8cc1d59098fc2e24219908855713317c','dropDefaultValue columnName=email, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-27','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',28,'EXECUTED','7:de9c1cfe5b771e385742658192bf10a3','dropDefaultValue columnName=event_type, tableName=jhi_persistent_audit_event','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-28','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',29,'EXECUTED','7:4890df99882170149a47f36c80f4ce38','dropDefaultValue columnName=first_name, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-29','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',30,'EXECUTED','7:75319e33742eeef13b01c6c156425275','dropDefaultValue columnName=image_url, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-30','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',31,'EXECUTED','7:83e98119c120277c7e17fe3470a8bf8e','dropDefaultValue columnName=lang_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-31','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',32,'EXECUTED','7:323f3047873d9c5b284de40b5881bb89','dropDefaultValue columnName=last_modified_by, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-32','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',33,'EXECUTED','7:aa0afacab9b39e122c2605ee39dddbb4','dropDefaultValue columnName=last_name, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-33','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',34,'EXECUTED','7:13e9c7c3cf6fa9168cf0d109def4409f','dropDefaultValue columnName=reset_key, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545061695176-34','Kacper (generated)','config/liquibase/changelog/20181217154803_changelog.xml','2019-01-09 00:13:49',35,'EXECUTED','7:4d2c9e85eeecef4b597611537c9fad13','dropDefaultValue columnName=value, tableName=jhi_persistent_audit_evt_data','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-1','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',36,'EXECUTED','7:800ef3269aa70d9b1394dd93a39f8370','dropUniqueConstraint constraintName=UC_INSPECTIONSUUID_COL, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-2','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',37,'EXECUTED','7:9ac30e74165c99a233a8d7446a004aac','dropUniqueConstraint constraintName=UC_INSURANCESUUID_COL, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-3','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',38,'EXECUTED','7:f883641bc8db829dba3932665e63e1ac','dropUniqueConstraint constraintName=UC_REFUELSUUID_COL, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-4','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',39,'EXECUTED','7:8ef3059ac7d8d2370c69d3d4e91dd9a1','dropUniqueConstraint constraintName=UC_REPAIRSUUID_COL, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-5','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',40,'EXECUTED','7:e093094a000bdd8e771c3e616882fb1e','dropUniqueConstraint constraintName=UC_ROUTINE_SERVICESUUID_COL, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-6','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',41,'EXECUTED','7:5811915c21d3e79c189093e79fc526ae','dropUniqueConstraint constraintName=UC_VEHICLESUUID_COL, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-7','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',42,'EXECUTED','7:b5a8dfcb7f6b3eaffa40fcf433f36b0a','dropColumn columnName=uuid, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-8','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',43,'EXECUTED','7:7d3082e313a027b72a917233fb9bf09b','dropColumn columnName=uuid, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-9','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',44,'EXECUTED','7:6e16283710b8a5cbf6275ed0eaf9b04b','dropColumn columnName=uuid, tableName=refuels','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-10','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',45,'EXECUTED','7:243caaf0e0020f5eea16e319dcc94d9f','dropColumn columnName=uuid, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-11','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',46,'EXECUTED','7:40193f54468710b224d31b12cc867ebc','dropColumn columnName=uuid, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-12','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',47,'EXECUTED','7:f5d7a123009cfdf5233c255eb26b1171','dropColumn columnName=uuid, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-13','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',48,'EXECUTED','7:ee35373fea60bedaa7b6011c224c02bf','dropDefaultValue columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-14','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',49,'EXECUTED','7:06d91f24ac30f159188b9bd2acc4970d','dropDefaultValue columnName=image_content_type, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-15','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',50,'EXECUTED','7:d40ffe232dc7572a6f1f793ebd7ec306','dropDefaultValue columnName=insurer, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-16','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',51,'EXECUTED','7:2adef877c118340ec0e042351764de29','dropDefaultValue columnName=model_suffix, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-17','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',52,'EXECUTED','7:c56465ab52221a5ed0632be3211ed928','dropDefaultValue columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-18','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',53,'EXECUTED','7:0fff00b8125ed48ce215425546c10252','dropDefaultValue columnName=number, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-19','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',54,'EXECUTED','7:a3520b953370a6afd8f0dcd5b381e7fa','dropDefaultValue columnName=registration_certificate, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-20','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',55,'EXECUTED','7:73aa398d514e473e3e6b9a45a7c15d01','dropDefaultValue columnName=vehicle_card, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1545416098210-21','Kacper (generated)','config/liquibase/changelog/20181221181442_changelog.xml','2019-01-09 00:13:49',56,'EXECUTED','7:cd780528436784debfb10ed188458917','dropDefaultValue columnName=vin_number, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-1','Kacper (generated)','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',57,'EXECUTED','7:8e3a7f149cd12b08ad51db5976b5ca14','addNotNullConstraint columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-2','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',58,'EXECUTED','7:ce27a66a346667c5f06ed28afad11373','modifyDataType columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-3','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',59,'EXECUTED','7:63f5b0fbeb68a5ce35bce3267b0875db','modifyDataType columnName=details, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-4','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',60,'EXECUTED','7:657e87913e3cd37ec871935d471716d2','modifyDataType columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-5','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',61,'EXECUTED','7:a3b4d1d4c402584c608f8f0cbc22f89b','modifyDataType columnName=details, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546467663093-6','Kacper','config/liquibase/changelog/20190102222057_changelog.xml','2019-01-09 00:13:49',62,'EXECUTED','7:e696d62b3a32e60f35f9d73ac6e2df20','modifyDataType columnName=details, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546547935872-1','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-09 00:13:49',63,'EXECUTED','7:4f6a8f2f56680c0ec38cafb091a349c7','addNotNullConstraint columnName=details, tableName=inspections; dropDefaultValue columnName=details, tableName=inspections','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546547935872-2','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-09 00:13:49',64,'EXECUTED','7:ba30ecff44894234eb28c3ab9c543219','addNotNullConstraint columnName=details, tableName=insurances; dropDefaultValue columnName=details, tableName=insurances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546547935872-3','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-09 00:13:49',65,'EXECUTED','7:4500dcf9402efc83380612e36dd954b1','addNotNullConstraint columnName=details, tableName=repairs; dropDefaultValue columnName=details, tableName=repairs','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546547935872-4','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-09 00:13:49',66,'EXECUTED','7:1265ba7ec438672dbd8991f0f2e42f1d','addNotNullConstraint columnName=details, tableName=routine_services; dropDefaultValue columnName=details, tableName=routine_services','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546547935872-5','Kacper (generated)','config/liquibase/changelog/20190103203841_changelog.xml','2019-01-09 00:13:49',67,'EXECUTED','7:e7fc087dbfde31990697a505a2a9a0ee','dropDefaultValue columnName=notes, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546548430063-1','Kacper (generated)','config/liquibase/changelog/20190103204656_changelog.xml','2019-01-09 00:13:49',68,'EXECUTED','7:daa93446a5a1b258aa76dd3a45509bfb','dropColumn columnName=image_content_type, tableName=vehicles','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-1','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',69,'EXECUTED','7:bc8af992875eb1858a99f080f61aeb05','createTable tableName=reminder_advances','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-2','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',70,'EXECUTED','7:8cfcd06aa114ab5128bc66c4c791acad','addColumn tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-3','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',71,'EXECUTED','7:48bd13e2b8fd8a9680a36a20380bc0d9','addColumn tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-4','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',72,'EXECUTED','7:2aaae785d89367e5ba056cce61a1dfe4','addColumn tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-5','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',73,'EXECUTED','7:2fd9e8fb62848ee538323c88b8d27e4e','addColumn tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-6','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',74,'EXECUTED','7:514d91695bc49200c3f049d0481037fb','addUniqueConstraint constraintName=UC_FUEL_TYPESENGLISH_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-7','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',75,'EXECUTED','7:7ff8f3b2cca73bc052b9694ba2c0cb19','addUniqueConstraint constraintName=UC_FUEL_TYPESPOLISH_COL, tableName=fuel_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-8','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',76,'EXECUTED','7:2c8cf603670d2a69f5486a31bc989645','addUniqueConstraint constraintName=UC_INSURANCE_TYPESENGLISH_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-9','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',77,'EXECUTED','7:9e8382d58b2afef514e4381dacead342','addUniqueConstraint constraintName=UC_INSURANCE_TYPESPOLISH_COL, tableName=insurance_types','',NULL,'3.5.4',NULL,NULL,'6992828099'),('1546875049766-10','Kacper (generated)','config/liquibase/changelog/20190107153035_changelog.xml','2019-01-09 00:13:49',78,'EXECUTED','7:11909b25ad79c4ffbda5faf06ac6c57b','addUniqueConstraint constraintName=UC_REMINDER_ADVANCESTYPE_COL, tableName=reminder_advances','',NULL,'3.5.4',NULL,NULL,'6992828099');
/*!40000 ALTER TABLE `databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `databasechangeloglock`
--

DROP TABLE IF EXISTS `databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `databasechangeloglock` (
  `ID` int(11) NOT NULL,
  `LOCKED` bit(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `databasechangeloglock`
--

LOCK TABLES `databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `databasechangeloglock` DISABLE KEYS */;
INSERT INTO `databasechangeloglock` VALUES (1,'\0',NULL,NULL);
/*!40000 ALTER TABLE `databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fuel_types`
--

DROP TABLE IF EXISTS `fuel_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `fuel_types` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `english` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `polish` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_FUEL_TYPESTYPE_COL` (`type`),
  UNIQUE KEY `UC_FUEL_TYPESENGLISH_COL` (`english`),
  UNIQUE KEY `UC_FUEL_TYPESPOLISH_COL` (`polish`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fuel_types`
--

LOCK TABLES `fuel_types` WRITE;
/*!40000 ALTER TABLE `fuel_types` DISABLE KEYS */;
INSERT INTO `fuel_types` VALUES (1,'DIESEL','DIESEL','DIESEL'),(2,'PETROL','PETROL','BENZYNA'),(3,'LPG','LPG','LPG'),(4,'CNG','CNG','CNG'),(5,'HYBRID','HYBRID','HYBRYDOWY'),(6,'ELECTRIC','ELECTRIC','ELEKTRYCZNY'),(7,'OTHER','Other','Inny');
/*!40000 ALTER TABLE `fuel_types` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspections`
--

DROP TABLE IF EXISTS `inspections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `inspections` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cost_in_cents` int(11) NOT NULL,
  `details` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `station` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `valid_thru` date NOT NULL,
  `date` date NOT NULL,
  `mileage` int(11) NOT NULL,
  `vehicle_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlfc8sgfw636xmcre6gj9ra4pe` (`vehicle_id`),
  CONSTRAINT `FKlfc8sgfw636xmcre6gj9ra4pe` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspections`
--

LOCK TABLES `inspections` WRITE;
/*!40000 ALTER TABLE `inspections` DISABLE KEYS */;
INSERT INTO `inspections` VALUES (1,9700,'OK','AutoDiagnostic','2019-02-03','2018-02-03',100800,1),(2,9700,'OK','AutoDiagnostic','2018-02-03','2017-02-03',89700,1),(3,9700,'OK','DiagnoStation','2018-01-14','2017-01-15',180700,3),(4,9700,'OK','DiagnoPro','2019-01-14','2018-01-15',200700,3);
/*!40000 ALTER TABLE `inspections` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `insurance_types`
--

DROP TABLE IF EXISTS `insurance_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `insurance_types` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `english` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `polish` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_INSURANCE_TYPESTYPE_COL` (`type`),
  UNIQUE KEY `UC_INSURANCE_TYPESENGLISH_COL` (`english`),
  UNIQUE KEY `UC_INSURANCE_TYPESPOLISH_COL` (`polish`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `insurance_types`
--

LOCK TABLES `insurance_types` WRITE;
/*!40000 ALTER TABLE `insurance_types` DISABLE KEYS */;
INSERT INTO `insurance_types` VALUES (1,'LI','LIABILITY INSURANCE','OC'),(2,'CC','COMPREHENSIVE COVER','AC'),(3,'OTHER','Other','Inne');
/*!40000 ALTER TABLE `insurance_types` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `insurances`
--

DROP TABLE IF EXISTS `insurances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `insurances` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cost_in_cents` int(11) NOT NULL,
  `details` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `insurer` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `valid_from` date NOT NULL,
  `valid_thru` date NOT NULL,
  `date` date NOT NULL,
  `mileage` int(11) NOT NULL,
  `insurance_type_id` bigint(20) NOT NULL,
  `vehicle_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK24ys5lgfug2q78d310u04tylf` (`insurance_type_id`),
  KEY `FKk7a7uqrkf4cuvn4w2rsdymafk` (`vehicle_id`),
  CONSTRAINT `FK24ys5lgfug2q78d310u04tylf` FOREIGN KEY (`insurance_type_id`) REFERENCES `insurance_types` (`id`),
  CONSTRAINT `FKk7a7uqrkf4cuvn4w2rsdymafk` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `insurances`
--

LOCK TABLES `insurances` WRITE;
/*!40000 ALTER TABLE `insurances` DISABLE KEYS */;
INSERT INTO `insurances` VALUES (1,70000,'Z NNW','YourInsurance','XXX-YYY-ZZZ-2018','2018-02-02','2019-02-02','2018-01-16',100300,1,1),(2,75000,'','YourInsurance','YYY-XXX-ZZZ-2018','2018-02-02','2019-02-02','2018-01-16',100300,2,1),(3,68000,'','Insurer','ZZZ-XXX-AAA-2017','2017-02-02','2018-02-01','2017-01-20',90000,1,1),(4,90000,'','Insurances','ASDF-QWER-ZXC-2018','2017-02-20','2018-02-20','2017-02-14',180000,1,3),(5,95000,'Zawiera NNW','Insurances','ZXC-ASD-QWE-2018','2018-02-20','2019-02-20','2018-02-10',201600,1,3);
/*!40000 ALTER TABLE `insurances` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_authority`
--

DROP TABLE IF EXISTS `jhi_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_authority` (
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_authority`
--

LOCK TABLES `jhi_authority` WRITE;
/*!40000 ALTER TABLE `jhi_authority` DISABLE KEYS */;
INSERT INTO `jhi_authority` VALUES ('ROLE_ADMIN'),('ROLE_USER');
/*!40000 ALTER TABLE `jhi_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_persistent_audit_event`
--

DROP TABLE IF EXISTS `jhi_persistent_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_persistent_audit_event` (
  `event_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `principal` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_date` timestamp NULL DEFAULT NULL,
  `event_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_persistent_audit_event` (`principal`,`event_date`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_persistent_audit_event`
--

LOCK TABLES `jhi_persistent_audit_event` WRITE;
/*!40000 ALTER TABLE `jhi_persistent_audit_event` DISABLE KEYS */;
INSERT INTO `jhi_persistent_audit_event` VALUES (1,'testUser','2019-01-09 00:16:51','AUTHENTICATION_FAILURE'),(2,'testuser','2019-01-09 00:16:58','AUTHENTICATION_SUCCESS'),(3,'admin','2019-01-09 00:17:04','AUTHENTICATION_SUCCESS'),(4,'testuser','2019-01-09 00:23:20','AUTHENTICATION_SUCCESS'),(5,'testuser','2019-01-09 00:58:37','AUTHENTICATION_SUCCESS'),(6,'admin','2019-01-09 01:18:21','AUTHENTICATION_SUCCESS'),(7,'testuser','2019-01-09 01:18:29','AUTHENTICATION_SUCCESS'),(8,'userTest','2019-01-09 08:02:36','AUTHENTICATION_FAILURE'),(9,'testUser','2019-01-09 08:02:43','AUTHENTICATION_FAILURE'),(10,'testuser','2019-01-09 08:02:49','AUTHENTICATION_SUCCESS'),(11,'testuser','2019-01-09 09:22:55','AUTHENTICATION_SUCCESS'),(12,'testuser','2019-01-10 21:17:30','AUTHENTICATION_SUCCESS');
/*!40000 ALTER TABLE `jhi_persistent_audit_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_persistent_audit_evt_data`
--

DROP TABLE IF EXISTS `jhi_persistent_audit_evt_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_persistent_audit_evt_data` (
  `event_id` bigint(20) NOT NULL,
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`event_id`,`name`),
  KEY `idx_persistent_audit_evt_data` (`event_id`),
  CONSTRAINT `fk_evt_pers_audit_evt_data` FOREIGN KEY (`event_id`) REFERENCES `jhi_persistent_audit_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_persistent_audit_evt_data`
--

LOCK TABLES `jhi_persistent_audit_evt_data` WRITE;
/*!40000 ALTER TABLE `jhi_persistent_audit_evt_data` DISABLE KEYS */;
INSERT INTO `jhi_persistent_audit_evt_data` VALUES (1,'message','Bad credentials'),(1,'type','org.springframework.security.authentication.BadCredentialsException'),(8,'message','Bad credentials'),(8,'type','org.springframework.security.authentication.BadCredentialsException'),(9,'message','Bad credentials'),(9,'type','org.springframework.security.authentication.BadCredentialsException');
/*!40000 ALTER TABLE `jhi_persistent_audit_evt_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user`
--

DROP TABLE IF EXISTS `jhi_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `login` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `first_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(254) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `activated` bit(1) NOT NULL,
  `lang_key` varchar(6) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `activation_key` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reset_key` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_user_login` (`login`),
  UNIQUE KEY `ux_user_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user`
--

LOCK TABLES `jhi_user` WRITE;
/*!40000 ALTER TABLE `jhi_user` DISABLE KEYS */;
INSERT INTO `jhi_user` VALUES (1,'system','$2a$10$mE.qmcV0mFU5NcKh73TZx.z4ueI/.bDWbj0T1BYyqP481kGGarKLG','System','System','system@localhost','','','en',NULL,NULL,'system','2019-01-09 00:13:48',NULL,'system',NULL),(2,'anonymoususer','$2a$10$j8S5d7Sr7.8VTOYNviDPOeWX8KcYILUVJBsYV83Y5NtECayypx9lO','Anonymous','User','anonymous@localhost','','','en',NULL,NULL,'system','2019-01-09 00:13:48',NULL,'system',NULL),(3,'admin','$2a$10$gSAhZrxMllrbgj/kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1/oRrC','Administrator','Administrator','admin@localhost','','','en',NULL,NULL,'system','2019-01-09 00:13:48',NULL,'system',NULL),(4,'user','$2a$10$VEjxo0jq2YG9Rbk2HmX9S.k1uZBGYUHdUcid3g/vfiEl7lwWgOH/K','User','User','user@localhost','','','en',NULL,NULL,'system','2019-01-09 00:13:48',NULL,'system',NULL),(5,'testuser','$2a$10$3if98GHKIMZpxniMI4z51.SDKDMNbdr5zUSHZTkJrKE.iq7pzUIfq','Test','Testowy','kcpr51@hotmail.com',NULL,'','pl',NULL,NULL,'anonymousUser','2019-01-09 00:16:08',NULL,'admin','2019-01-09 00:17:24');
/*!40000 ALTER TABLE `jhi_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user_authority`
--

DROP TABLE IF EXISTS `jhi_user_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_user_authority` (
  `user_id` bigint(20) NOT NULL,
  `authority_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`user_id`,`authority_name`),
  KEY `fk_authority_name` (`authority_name`),
  CONSTRAINT `fk_authority_name` FOREIGN KEY (`authority_name`) REFERENCES `jhi_authority` (`name`),
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user_authority`
--

LOCK TABLES `jhi_user_authority` WRITE;
/*!40000 ALTER TABLE `jhi_user_authority` DISABLE KEYS */;
INSERT INTO `jhi_user_authority` VALUES (1,'ROLE_ADMIN'),(1,'ROLE_USER'),(3,'ROLE_ADMIN'),(3,'ROLE_USER'),(4,'ROLE_USER'),(5,'ROLE_ADMIN'),(5,'ROLE_USER');
/*!40000 ALTER TABLE `jhi_user_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refuels`
--

DROP TABLE IF EXISTS `refuels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `refuels` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cost_in_cents` int(11) NOT NULL,
  `station` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date` date NOT NULL,
  `mileage` int(11) NOT NULL,
  `volume_in_cm3` int(11) DEFAULT NULL,
  `vehicle_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7cs5c7iw40lj73yo6s77u7rvl` (`vehicle_id`),
  CONSTRAINT `FK7cs5c7iw40lj73yo6s77u7rvl` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refuels`
--

LOCK TABLES `refuels` WRITE;
/*!40000 ALTER TABLE `refuels` DISABLE KEYS */;
INSERT INTO `refuels` VALUES (1,10000,'CPN','2018-01-04',100000,21500,1),(2,10000,'','2018-01-20',100389,22500,1),(3,10000,'','2018-02-10',100800,22000,1),(4,10000,'','2018-02-28',101400,23000,1),(5,10000,'','2018-03-14',101680,21000,1),(6,10000,'CPN','2018-04-02',102100,21750,1),(7,10000,'','2018-04-20',102500,22000,1),(8,10000,'','2018-05-07',102879,22225,1),(9,12500,'','2018-05-26',103300,27500,1),(10,10000,'','2018-06-12',104005,22000,1),(11,12500,'','2018-07-02',104500,27250,1),(12,11175,'CPN','2018-07-14',104850,25000,1),(13,15000,'Shell Italy','2018-07-15',105862,25050,1),(14,15000,'Shell Italy','2018-07-30',106425,25125,1),(15,10000,'CPN','2018-08-13',107000,22000,1),(16,11000,'','2018-09-01',107450,25000,1),(17,10000,'','2018-09-15',108005,21750,1),(18,10000,'','2018-09-29',108500,21500,1),(19,10000,'','2018-10-12',109001,21250,1),(20,10000,'','2018-10-28',109400,22000,1),(21,10000,'','2018-11-14',110000,21225,1),(22,11000,'','2018-12-01',110580,25000,1),(23,10000,'CPN','2018-12-22',111011,22000,1),(24,33800,'CPN','2018-01-04',200000,67188,3),(25,33800,'','2018-01-20',200778,70313,3),(26,33800,'','2018-02-10',201600,68750,3),(27,33800,'','2018-02-28',202800,71875,3),(28,33800,'','2018-03-14',203360,65625,3),(29,33800,'CPN','2018-04-02',204200,67970,3),(30,33800,'','2018-04-20',205000,68750,3),(31,33800,'','2018-05-07',205758,69453,3),(32,42250,'','2018-05-26',206600,85938,3),(33,33800,'','2018-06-12',208010,68750,3),(34,42250,'','2018-07-02',209000,85158,3),(35,37773,'CPN','2018-07-14',209700,78125,3),(36,50700,'Shell Italy','2018-07-15',211724,78283,3),(37,50700,'Shell Italy','2018-07-30',212850,78515,3),(38,33800,'CPN','2018-08-13',214000,68750,3),(39,37180,'','2018-09-01',214900,78125,3),(40,33800,'','2018-09-15',216010,67970,3),(41,33800,'','2018-09-29',217000,67188,3),(42,33800,'','2018-10-12',218002,66408,3),(43,33800,'','2018-10-28',218800,68750,3),(44,33800,'','2018-11-14',220000,66328,3),(45,37180,'','2018-12-01',221160,78125,3),(46,33800,'CPN','2018-12-22',222022,68750,3);
/*!40000 ALTER TABLE `refuels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reminder_advances`
--

DROP TABLE IF EXISTS `reminder_advances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `reminder_advances` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_REMINDER_ADVANCESTYPE_COL` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminder_advances`
--

LOCK TABLES `reminder_advances` WRITE;
/*!40000 ALTER TABLE `reminder_advances` DISABLE KEYS */;
INSERT INTO `reminder_advances` VALUES (1,3),(2,7),(3,14),(4,30);
/*!40000 ALTER TABLE `reminder_advances` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `repairs`
--

DROP TABLE IF EXISTS `repairs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `repairs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cost_in_cents` int(11) NOT NULL,
  `details` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `station` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date` date NOT NULL,
  `mileage` int(11) NOT NULL,
  `vehicle_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr8rwhlbv43kxbn4j93hkul7ax` (`vehicle_id`),
  CONSTRAINT `FKr8rwhlbv43kxbn4j93hkul7ax` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `repairs`
--

LOCK TABLES `repairs` WRITE;
/*!40000 ALTER TABLE `repairs` DISABLE KEYS */;
INSERT INTO `repairs` VALUES (1,20000,'Poduszka pod silnikiem','AutoService','2018-04-20',102500,1),(2,50000,'Malowanie zderzaka','Blacharstwo','2018-11-05',109800,1),(3,250000,'Sprzęgło dwumasowe','AutoProf','2018-08-30',214880,3);
/*!40000 ALTER TABLE `repairs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `routine_services`
--

DROP TABLE IF EXISTS `routine_services`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `routine_services` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cost_in_cents` int(11) NOT NULL,
  `details` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `next_by_date` date DEFAULT NULL,
  `next_by_mileage` int(11) DEFAULT NULL,
  `station` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date` date NOT NULL,
  `mileage` int(11) NOT NULL,
  `vehicle_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsy0jrbbtf29lpv37ahmtlj3dv` (`vehicle_id`),
  CONSTRAINT `FKsy0jrbbtf29lpv37ahmtlj3dv` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `routine_services`
--

LOCK TABLES `routine_services` WRITE;
/*!40000 ALTER TABLE `routine_services` DISABLE KEYS */;
INSERT INTO `routine_services` VALUES (1,30000,'Olej\nFiltr oleju\nFiltr paliwa','2020-02-26',116200,'AutoService','2018-02-26',101200,1),(2,8000,'Wymiana opon','2018-10-22',0,'OponySerwis','2018-04-09',102200,1),(3,8000,'Wymiana opon\nWyważanie kół','2019-04-09',0,'OponySerwis','2018-10-20',109200,1),(4,8000,'Wymiana opon','2019-10-21',0,'Opony u Tomka','2018-04-23',205200,3),(5,8000,'Wymiana opon','2019-04-22',0,'Opony u Tomka','2018-10-20',218400,3),(6,50000,'Wymiana łańcucha rozrządu','2030-01-31',400000,'AutoProf','2018-03-14',203460,3);
/*!40000 ALTER TABLE `routine_services` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `vehicles`
--

DROP TABLE IF EXISTS `vehicles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `vehicles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `license_plate` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `make` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `engine_power_in_kw` int(11) DEFAULT NULL,
  `engine_volume_in_cm3` int(11) DEFAULT NULL,
  `image` longblob DEFAULT NULL,
  `model_suffix` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` longtext COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `registration_certificate` varchar(14) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vehicle_card` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vin_number` varchar(17) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `weight_in_kg` int(11) DEFAULT NULL,
  `year_of_manufacture` int(11) DEFAULT NULL,
  `fuel_type_id` bigint(20) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKck94koff5phplxnts3lahjinu` (`fuel_type_id`),
  KEY `FKhm05kh6d8f082pgddom1q1yco` (`owner_id`),
  CONSTRAINT `FKck94koff5phplxnts3lahjinu` FOREIGN KEY (`fuel_type_id`) REFERENCES `fuel_types` (`id`),
  CONSTRAINT `FKhm05kh6d8f082pgddom1q1yco` FOREIGN KEY (`owner_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `vehicles`
--

LOCK TABLES `vehicles` WRITE;
/*!40000 ALTER TABLE `vehicles` DISABLE KEYS */;
INSERT INTO `vehicles` VALUES (1,'DTR 2963','Ford','Focus',80,1800,'','MK2','Przykładowa notatka','FA/ZY551352877','OBY6919921','LWZPOZUFADPRLGRC0',1300,2008,1,5),(2,'DB 7087','Fiat','Tipo',90,1600,'','','','CR/QM662072044','JRF3585827','JKISATJ6FVVWXDM0W',1150,2018,2,5),(3,'DTR 5713','BMW','X5',140,3500,'','3','','BZ/IR903472581','ZZC6122833','QBU6UX6WWDTJFHCJM',2100,2012,1,5);
/*!40000 ALTER TABLE `vehicles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'carcare'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-01-10 21:21:31
