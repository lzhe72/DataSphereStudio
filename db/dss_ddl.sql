SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for dss_application
-- ----------------------------
DROP TABLE IF EXISTS `dss_application`;
CREATE TABLE `dss_application` (
  `id` int(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) DEFAULT NULL,
  `url` varchar(128) DEFAULT NULL,
  `is_user_need_init` tinyint(1) DEFAULT NULL,
  `level` int(8) DEFAULT NULL,
  `user_init_url` varchar(255) DEFAULT NULL,
  `exists_project_service` tinyint(1) DEFAULT NULL,
  `project_url` varchar(255) DEFAULT NULL,
  `enhance_json` varchar(255) DEFAULT NULL,
  `if_iframe` tinyint(1) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `redirect_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- ----------------------------
-- Table structure for dss_application_user_init_result
-- ----------------------------
DROP TABLE IF EXISTS `dss_application_user_init_result`;
CREATE TABLE `dss_application_user_init_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `application_id` int(11) DEFAULT NULL,
  `result` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `is_init_success` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- ----------------------------
-- Table structure for dss_workflow_node
-- ----------------------------
DROP TABLE IF EXISTS `dss_workflow_node`;
CREATE TABLE `dss_workflow_node` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `icon` text,
  `node_type` varchar(255) DEFAULT NULL,
  `application_id` int(20) DEFAULT NULL,
  `submit_to_scheduler` tinyint(1) DEFAULT NULL,
  `enable_copy` tinyint(1) DEFAULT NULL,
  `should_creation_before_node` tinyint(1) DEFAULT NULL,
  `support_jump` tinyint(1) DEFAULT NULL,
  `jump_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for dss_flow
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow`;
CREATE TABLE `dss_flow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) DEFAULT NULL,
  `state` tinyint(1) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `creator_id` bigint(20) DEFAULT NULL,
  `is_root_flow` tinyint(1) DEFAULT NULL,
  `rank` int(10) DEFAULT NULL,
  `project_id` bigint(20) DEFAULT NULL,
  `has_saved` tinyint(1) DEFAULT NULL,
  `uses` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `name` (`name`,`project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;


-- ----------------------------
-- Table structure for dss_flow_publish_history
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow_publish_history`;
CREATE TABLE `dss_flow_publish_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `flow_version_id` bigint(20) DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `publisher_id` bigint(255) DEFAULT NULL,
  `alert_flag` tinyint(1) DEFAULT NULL,
  `alter_config` varchar(255) DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `state` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_flow_relation
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow_relation`;
CREATE TABLE `dss_flow_relation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `flow_id` bigint(20) DEFAULT NULL,
  `parent_flow_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_flow_taxonomy
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow_taxonomy`;
CREATE TABLE `dss_flow_taxonomy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(20) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `creator_id` int(11) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `project_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `name` (`name`,`project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_flow_taxonomy_relation
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow_taxonomy_relation`;
CREATE TABLE `dss_flow_taxonomy_relation` (
  `taxonomy_id` bigint(20) NOT NULL,
  `flow_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;


-- ----------------------------
-- Table structure for dss_flow_version
-- ----------------------------
DROP TABLE IF EXISTS `dss_flow_version`;
CREATE TABLE `dss_flow_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `flow_id` bigint(20) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `version` varchar(255) DEFAULT NULL,
  `json_path` text,
  `comment` varchar(255) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `updator_id` bigint(255) DEFAULT NULL,
  `project_version_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;


-- ----------------------------
-- Table structure for dss_project
-- ----------------------------
DROP TABLE IF EXISTS `dss_project`;
CREATE TABLE `dss_project` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8_bin DEFAULT NULL,
  `source` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT 'Source of the dss_project',
  `description` text COLLATE utf8_bin,
  `user_id` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `create_by` bigint(20) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `org_id` bigint(20) DEFAULT NULL COMMENT 'Organization ID',
  `visibility` bit(1) DEFAULT NULL,
  `is_transfer` bit(1) DEFAULT NULL COMMENT 'Reserved word',
  `initial_org_id` bigint(20) DEFAULT NULL,
  `isArchive` bit(1) DEFAULT b'0' COMMENT 'If it is archived',
  `pic` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `star_num` int(11) DEFAULT '0',
  `product` varchar(200) COLLATE utf8_bin DEFAULT NULL,
  `application_area` tinyint(1) DEFAULT NULL,
  `business` varchar(200) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin ROW_FORMAT=COMPACT;


-- ----------------------------
-- Table structure for dss_project_applications_project
-- ----------------------------
DROP TABLE IF EXISTS `dss_project_applications_project`;
CREATE TABLE `dss_project_applications_project` (
  `project_id` bigint(20) NOT NULL,
  `application_id` int(11) NOT NULL,
  `application_project_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- ----------------------------
-- Table structure for dss_project_publish_history
-- ----------------------------
DROP TABLE IF EXISTS `dss_project_publish_history`;
CREATE TABLE `dss_project_publish_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `project_version_id` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `creator_id` bigint(20) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `comment` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `state` tinyint(255) DEFAULT NULL,
  `version_path` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `project_version_id` (`project_version_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_project_taxonomy
-- ----------------------------
DROP TABLE IF EXISTS `dss_project_taxonomy`;
CREATE TABLE `dss_project_taxonomy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(20) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `creator_id` int(11) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `name` (`name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_project_taxonomy_relation
-- ----------------------------
DROP TABLE IF EXISTS `dss_project_taxonomy_relation`;
CREATE TABLE `dss_project_taxonomy_relation` (
  `taxonomy_id` bigint(20) NOT NULL,
  `project_id` bigint(20) NOT NULL,
  `creator_id` bigint(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT;


-- ----------------------------
-- Table structure for dss_project_version
-- ----------------------------
DROP TABLE IF EXISTS `dss_project_version`;
CREATE TABLE `dss_project_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `project_id` bigint(20) DEFAULT NULL,
  `version` varchar(10) COLLATE utf8_bin DEFAULT NULL,
  `comment` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `updator_id` int(11) DEFAULT NULL,
  `lock` int(255) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin ROW_FORMAT=COMPACT;

-- ----------------------------
-- Table structure for dss_user
-- ----------------------------
DROP TABLE IF EXISTS `dss_user`;
CREATE TABLE `dss_user` (
  `id` int(11) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `name` varchar(64) DEFAULT NULL,
  `is_first_login` tinyint(1) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for event_auth
-- ----------------------------
DROP TABLE IF EXISTS `event_auth`;
CREATE TABLE `event_auth` (
  `sender` varchar(45) NOT NULL COMMENT '???????????????',
  `topic` varchar(45) NOT NULL COMMENT '????????????',
  `msg_name` varchar(45) NOT NULL COMMENT '????????????',
  `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '??????????????????',
  `allow_send` int(11) NOT NULL COMMENT '??????????????????',
  PRIMARY KEY (`sender`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='?????????????????????';

-- ----------------------------
-- Table structure for event_queue
-- ----------------------------
DROP TABLE IF EXISTS `event_queue`;
CREATE TABLE `event_queue` (
  `msg_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '??????ID???',
  `sender` varchar(45) NOT NULL COMMENT '???????????????',
  `send_time` datetime NOT NULL COMMENT '??????????????????',
  `topic` varchar(45) NOT NULL COMMENT '????????????',
  `msg_name` varchar(45) NOT NULL COMMENT '????????????',
  `msg` varchar(250) DEFAULT NULL COMMENT '????????????',
  `send_ip` varchar(45) NOT NULL,
  PRIMARY KEY (`msg_id`)
) ENGINE=InnoDB AUTO_INCREMENT=154465 DEFAULT CHARSET=utf8 COMMENT='azkaban???????????????????????????';

-- ----------------------------
-- Table structure for event_status
-- ----------------------------
DROP TABLE IF EXISTS `event_status`;
CREATE TABLE `event_status` (
  `receiver` varchar(45) NOT NULL COMMENT '???????????????',
  `receive_time` datetime NOT NULL COMMENT '??????????????????',
  `topic` varchar(45) NOT NULL COMMENT '????????????',
  `msg_name` varchar(45) NOT NULL COMMENT '????????????',
  `msg_id` int(11) NOT NULL COMMENT '?????????????????????id',
  PRIMARY KEY (`receiver`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='?????????????????????';
