-- ----------------------------
-- Flowable Menus
-- ----------------------------
INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (NULL, 5, 0, '工作流', 'Flowable', NULL, 100, 'flow', 'flowable', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

SET @parentId = LAST_INSERT_ID();

INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '启动流程', 'ProcessStart', 'flowable/process/index', 1, 'monitor', 'process/start', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '我的待办', 'MyToDo', 'flowable/task/index', 2, 'list', 'task/todo', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '我的已办', 'MyDone', 'flowable/task/finished', 3, 'checkbox', 'task/finished', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '流程定义', 'ProcessDefinition', 'flowable/definition/index', 4, 'dept', 'definition', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '流程实例', 'ProcessInstance', 'flowable/instance/index', 5, 'tree-table', 'instance', 0, 0, 0, NULL, 'admin', 'admin', NOW(), NOW());

-- Hidden Menu for Modeler
INSERT INTO `sys_menu` (`pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`)
VALUES (@parentId, 0, 1, '流程设计', 'ProcessModeler', 'flowable/modeler/index', 6, 'edit', 'modeler', 0, 0, 1, NULL, 'admin', 'admin', NOW(), NOW());

-- Assign Menus to Admin Role (Role ID 1)
-- Get IDs of inserted menus
SET @menuId1 = (SELECT menu_id FROM sys_menu WHERE name = 'Flowable');
SET @menuId2 = (SELECT menu_id FROM sys_menu WHERE name = 'ProcessStart');
SET @menuId3 = (SELECT menu_id FROM sys_menu WHERE name = 'MyToDo');
SET @menuId4 = (SELECT menu_id FROM sys_menu WHERE name = 'MyDone');
SET @menuId5 = (SELECT menu_id FROM sys_menu WHERE name = 'ProcessDefinition');
SET @menuId6 = (SELECT menu_id FROM sys_menu WHERE name = 'ProcessInstance');
SET @menuId7 = (SELECT menu_id FROM sys_menu WHERE name = 'ProcessModeler');

INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId1, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId2, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId3, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId4, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId5, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId6, 1);
INSERT INTO sys_roles_menus (menu_id, role_id) VALUES (@menuId7, 1);
