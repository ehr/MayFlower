DROP TABLE IF EXISTS `huge`.`code`;
CREATE TABLE  `huge`.`code` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `TITLE` varchar(45) NOT NULL COMMENT '�������',
  `AUTHOR` varchar(45) NOT NULL COMMENT '������',
  `CREATE_DATE` datetime DEFAULT NULL COMMENT '����ʱ��',
  `LASTMODIFY_DATE` datetime DEFAULT NULL COMMENT '����޸�ʱ��',
  `CONTENT` longtext COMMENT '�����',
  `TAG` varchar(45) DEFAULT NULL COMMENT '��ǩ',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=71 DEFAULT CHARSET=utf8 COMMENT='����';


