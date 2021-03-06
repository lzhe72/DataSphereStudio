/*
 * Copyright 2019 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.server.service.impl;


import com.webank.wedatasphere.dss.appjoint.exception.AppJointErrorException;
import com.webank.wedatasphere.dss.appjoint.scheduler.SchedulerAppJoint;
import com.webank.wedatasphere.dss.appjoint.scheduler.entity.SchedulerProject;
import com.webank.wedatasphere.dss.appjoint.scheduler.hooks.ProjectPublishHook;
import com.webank.wedatasphere.dss.appjoint.scheduler.parser.ProjectParser;
import com.webank.wedatasphere.dss.appjoint.scheduler.tuning.ProjectTuning;
import com.webank.wedatasphere.dss.appjoint.service.ProjectService;
import com.webank.wedatasphere.dss.application.entity.Application;
import com.webank.wedatasphere.dss.application.service.ApplicationService;
import com.webank.wedatasphere.dss.common.entity.flow.DWSFlow;
import com.webank.wedatasphere.dss.common.entity.flow.DWSFlowVersion;
import com.webank.wedatasphere.dss.common.entity.project.DWSProject;
import com.webank.wedatasphere.dss.common.entity.project.DWSProjectPublishHistory;
import com.webank.wedatasphere.dss.common.entity.project.DWSProjectVersion;
import com.webank.wedatasphere.dss.common.entity.project.Project;
import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.common.protocol.RequestDWSProject;
import com.webank.wedatasphere.dss.common.utils.DSSExceptionUtils;
import com.webank.wedatasphere.dss.server.constant.DSSServerConstant;
import com.webank.wedatasphere.dss.server.dao.*;
import com.webank.wedatasphere.dss.server.entity.DWSFlowTaxonomy;
import com.webank.wedatasphere.dss.server.entity.DWSProjectTaxonomyRelation;
import com.webank.wedatasphere.dss.server.function.FunctionInvoker;
import com.webank.wedatasphere.dss.server.lock.Lock;
import com.webank.wedatasphere.dss.server.lock.LockEnum;
import com.webank.wedatasphere.dss.server.service.BMLService;
import com.webank.wedatasphere.dss.server.service.DWSFlowService;
import com.webank.wedatasphere.dss.server.service.DWSProjectService;
import com.webank.wedatasphere.dss.server.util.ThreadPoolTool;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class DWSProjectServiceImpl implements DWSProjectService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProjectTaxonomyMapper projectTaxonomyMapper;
    @Autowired
    private DWSUserMapper dwsUserMapper;
    @Autowired
    private FlowMapper flowMapper;
    @Autowired
    private FlowTaxonomyMapper flowTaxonomyMapper;
    @Autowired
    private DWSFlowService flowService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private SchedulerAppJoint schedulerAppJoint;
    @Autowired
    private ProjectParser projectParser;
    @Autowired
    private ProjectTuning projectTuning;
    @Autowired
    private ProjectPublishHook[] projectPublishHooks;
    @Autowired
    private BMLService bmlService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private FunctionInvoker functionInvoker;

    @Override
    public DWSProject getProjectByID(Long id) {
        /*JdbcProjectImpl instance = wtssdbConnector.getInjector().getInstance(JdbcProjectImpl.class);
        Project project = instance.fetchProjectById(id.intValue());
        DWSProject dwsProject = EntityUtils.Project2DWSProject(project);*/
        return projectMapper.selectProjectByID(id);
    }

    @Transactional(rollbackFor = {DSSErrorException.class,AppJointErrorException.class})
    @Override
    public Long addProject(String userName, String name, String description, Long taxonomyID,String product,Integer applicationArea,String business) throws DSSErrorException, AppJointErrorException {
        DWSProject dwsProject = new DWSProject();
        dwsProject.setUserName(userName);
        dwsProject.setName(name);
        dwsProject.setDescription(description);
        //??????scheduler???project
        if(existSchesulis()){
            createSchedulerProject(dwsProject);
        }
        //??????appjoint???project
        Map<Long,Long> appjointProjectIDAndAppID = createAppjointProject(dwsProject);
        Long userID = dwsUserMapper.getUserID(userName);
        //??????dss?????????project
        Pair<Long, Long> pair = addDWSProject(userID, name, description,product,applicationArea,business);
        //????????????
        projectTaxonomyMapper.addProjectTaxonomyRelation(pair.getFirst(), taxonomyID, userID);
        if(!appjointProjectIDAndAppID.isEmpty())projectMapper.addAccessProjectRelation(appjointProjectIDAndAppID,pair.getFirst());
        return pair.getSecond();
    }



    private Map<Long,Long> createAppjointProject(DWSProject project) throws DSSErrorException, AppJointErrorException {
        Map applicationProjectIDs = new HashMap<Long,Long>();
        List<Pair<Project, String>> pairs = functionInvoker.projectServiceAddFunction(project, ProjectService::createProject, applicationService.listAppjoint());
        for (Pair<Project, String> pair : pairs) {
            if(pair.getFirst().getId() != null){
                applicationProjectIDs.put(applicationService.getApplication(pair.getSecond()).getId(),pair.getFirst().getId());
            }
        }
        return applicationProjectIDs;
    }

    private Pair<Long,Long> addDWSProject(Long userID, String name, String description,String product,Integer applicationArea,String business) {
        DWSProject dwsProject = new DWSProject();
        dwsProject.setUserID(userID);
        dwsProject.setName(name);
        dwsProject.setDescription(description);
        dwsProject.setSource(DSSServerConstant.DWS_PROJECT_SOURCE);
        dwsProject.setCreateTime(new Date());
        dwsProject.setCreateBy(userID);
        dwsProject.setProduct(product);
        dwsProject.setApplicationArea(applicationArea);
        dwsProject.setBusiness(business);
        projectMapper.addProject(dwsProject);
        DWSProjectVersion dwsProjectVersion = new DWSProjectVersion();
        dwsProjectVersion.setComment(DSSServerConstant.DWS_PROJECT_FIRST_VERSION_COMMENT);
        dwsProjectVersion.setProjectID(dwsProject.getId());
        dwsProjectVersion.setUpdateTime(new Date());
        dwsProjectVersion.setUpdatorID(userID);
        dwsProjectVersion.setVersion(DSSServerConstant.DWS_PROJECT_FIRST_VERSION);
        dwsProjectVersion.setLock(0);
        projectMapper.addProjectVersion(dwsProjectVersion);
        return new Pair<Long,Long>(dwsProject.getId(),dwsProjectVersion.getId());
    }

    private void createSchedulerProject(DWSProject dwsProject) throws DSSErrorException {
        try {
            functionInvoker.projectServiceAddFunction(dwsProject,ProjectService::createProject,Arrays.asList(schedulerAppJoint));
        } catch (Exception e) {
            logger.error("add scheduler project failed,", e);
            throw new DSSErrorException(90002, "add scheduler project failed" + e.getMessage());
        }
    }

    @Override
    public void updateProject(long projectID, String name, String description, String userName, String product ,Integer applicationArea ,String business) throws AppJointErrorException {
        // TODO: 2019/9/19  appjoint???update
        //??????????????????
        //??????wtssProject??????description
        if(!StringUtils.isBlank(description)){
            DWSProject project = getProjectByID(projectID);
            project.setUserName(userName);
            project.setDescription(description);
            if(existSchesulis()){
                functionInvoker.projectServiceFunction(project,ProjectService::updateProject,Arrays.asList(schedulerAppJoint));
            }
            functionInvoker.projectServiceFunction(project,ProjectService::updateProject,applicationService.listAppjoint());
        }
        projectMapper.updateDescription(projectID, description, product ,applicationArea ,business);
    }

    @Transactional(rollbackFor = DSSErrorException.class)
    @Override
    public void deleteProject(long projectID, String userName, Boolean ifDelScheduler) throws DSSErrorException {
        try {
            DWSProject project = getProjectByID(projectID);
            project.setUserName(userName);
            if(ifDelScheduler){
                if(existSchesulis()){
                    functionInvoker.projectServiceFunction(project,ProjectService::deleteProject,Arrays.asList(schedulerAppJoint));
                }
            }
            functionInvoker.projectServiceFunction(project,ProjectService::deleteProject,applicationService.listAppjoint());
            // TODO: 2019/11/15 ??????relations???
        } catch (Exception e) {
            logger.info("??????????????????????????????", e);
            String errorMsg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            throw new DSSErrorException(90012, errorMsg);
        }
        flowTaxonomyMapper.deleteFlowTaxonomyByProjectID(projectID);
        List<DWSFlow> dwsFlowList = flowMapper.listFlowByProjectID(projectID);
        flowService.batchDeleteFlow(dwsFlowList.stream().map(f -> f.getId()).distinct().collect(Collectors.toList()), null);
        projectMapper.deleteProjectVersions(projectID);
        projectMapper.deleteProjectBaseInfo(projectID);
        projectTaxonomyMapper.deleteProjectTaxonomyRelationByProjectID(projectID);
    }

    @Override
    public DWSProject getLatestVersionProject(Long projectID) {
        DWSProject dwsProject = getProjectByID(projectID);
        DWSProjectVersion dwsProjectVersion = projectMapper.selectLatestVersionByProjectID(projectID);
        dwsProject.setLatestVersion(dwsProjectVersion);
        return dwsProject;
    }

    /**
     * ??????projectVersionID????????????projrct??????????????????????????????????????????
     *
     * @param projectVersionID
     * @return
     */
    @Override
    public DWSProject getProjectByProjectVersionID(Long projectVersionID) {
        DWSProject dwsProject = projectMapper.selectProjectByVersionID(projectVersionID);
        return dwsProject;
    }

    @Override
    public Boolean isPublished(Long projectID) {
        return !projectMapper.noPublished(projectID);
    }

    @Override
    public List<DWSProjectVersion> listAllProjectVersions(Long projectID) {
        List<DWSProjectVersion> dwsProjectVersions = projectMapper.listProjectVersionsByProjectID(projectID);
        for (DWSProjectVersion dwsProjectVersion : dwsProjectVersions) {
            DWSProjectPublishHistory publishHistory = projectMapper.selectProjectPublishHistoryByProjectVersionID(dwsProjectVersion.getId());
            dwsProjectVersion.setPublishHistory(publishHistory);
        }
        return dwsProjectVersions;
    }


    @Lock(type = LockEnum.ADD)
    @Transactional(rollbackFor = {DSSErrorException.class, InterruptedException.class,AppJointErrorException.class})
    @Override
    public void publish(Long projectVersionID, String userName, String comment) throws DSSErrorException, InterruptedException, AppJointErrorException {
        // TODO: 2019/9/24 try catch ??????json?????????parser???
        //1.??????dwsProject
        DWSProject dwsProject = projectMapper.selectProjectByVersionID(projectVersionID);
        dwsProject.setUserName(dwsUserMapper.getuserName(dwsProject.getUserID()));
        logger.info(userName + "-?????????????????????" + dwsProject.getName() + "??????ID??????" + projectVersionID);
        ArrayList<DWSFlow> dwsFlows = new ArrayList<>();
        List<DWSFlowVersion> dwsFlowVersionList = flowMapper.listLatestRootFlowVersionByProjectVersionID(projectVersionID);
        for (DWSFlowVersion dwsFlowVersion : dwsFlowVersionList) {
            DWSFlow dwsFlow = flowMapper.selectFlowByID(dwsFlowVersion.getFlowID());
            String json = (String) bmlService.query(userName, dwsFlowVersion.getJsonPath(), dwsFlowVersion.getVersion()).get("string");
            if (!dwsFlow.getHasSaved()) {
                logger.info("?????????{}????????????????????????",dwsFlow.getName());
            } else if(StringUtils.isNotBlank(json)){
                dwsFlowVersion.setJson(json);
                dwsFlow.setLatestVersion(dwsFlowVersion);
                createPublishProject(userName, dwsFlowVersion.getFlowID(), dwsFlow, projectVersionID);
                dwsFlows.add(dwsFlow);
            } else {
                String warnMsg = String.format(DSSServerConstant.PUBLISH_FLOW_REPORT_FORMATE, dwsFlow.getName(), dwsFlowVersion.getVersion());
                logger.info(warnMsg);
                throw new DSSErrorException(90013, warnMsg);
            }
        }
        if (dwsFlows.isEmpty()) throw new DSSErrorException(90007, "???????????????????????????????????????,?????????????????????????????????");
        dwsProject.setFlows(dwsFlows);
        //2.??????dwsProject?????????????????????
        SchedulerProject schedulerProject = projectParser.parseProject(dwsProject);
        projectTuning.tuningSchedulerProject(schedulerProject);
        Stream.of(projectPublishHooks).forEach(DSSExceptionUtils.handling(hook -> hook.prePublish(schedulerProject)));
        (schedulerAppJoint.getProjectService()).publishProject(schedulerProject, schedulerAppJoint.getSecurityService().login(userName));
        Stream.of(projectPublishHooks).forEach(DSSExceptionUtils.handling(hook -> hook.postPublish(schedulerProject)));
        //3.???????????????????????????
        DWSProjectVersion dwsProjectVersion = projectMapper.selectProjectVersionByID(projectVersionID);
        copyProjectVersionMax(projectVersionID, dwsProjectVersion, dwsProjectVersion, userName, null);
    }

    @Override
    public Long createPublishHistory(String comment, Long creatorID, Long projectVersionID) {
        DWSProjectPublishHistory dwsProjectPublishHistory = new DWSProjectPublishHistory();
        dwsProjectPublishHistory.setComment(comment);
        dwsProjectPublishHistory.setCreateID(creatorID);
        dwsProjectPublishHistory.setCreateTime(new Date());
        dwsProjectPublishHistory.setUpdateTime(new Date());
        dwsProjectPublishHistory.setProjectVersionID(projectVersionID);
        dwsProjectPublishHistory.setState(0);
        projectMapper.insertPublishHistory(dwsProjectPublishHistory);
        return dwsProjectPublishHistory.getId();
    }

    @Override
    public void updatePublishHistory(Long projectVersionID, Integer status, Date updateTime) {
        projectMapper.updatePublishHistoryState(projectVersionID, status);
    }

    @Override
    public DWSProjectPublishHistory getPublishHistoryByID(Long projectVersionID) {
        return projectMapper.selectProjectPublishHistoryByProjectVersionID(projectVersionID);
    }

    @Override
    public DWSProject getExecutionDWSProject(RequestDWSProject requestDWSProject) throws DSSErrorException {
        DWSFlow dwsFlow = flowService.getOneVersionFlow(requestDWSProject.flowId(), requestDWSProject.version(),requestDWSProject.projectVersionId());
        DWSProject dwsProject = projectMapper.selectProjectByVersionID(requestDWSProject.projectVersionId());
        dwsProject.setUserName(dwsUserMapper.getuserName(dwsProject.getUserID()));
        DWSFlow returnFlow = recursiveGenerateParentFlow(dwsFlow,requestDWSProject);
        dwsProject.setFlows(Arrays.asList(returnFlow));
        return dwsProject;
    }

    @Override
    public Long getAppjointProjectID(Long projectID, String nodeType) {
        // TODO: 2019/11/15 ???????????????dss?????????
        Application applicationbyNodeType = applicationService.getApplicationbyNodeType(nodeType);
        Long appjointProjectID = projectMapper.getAppjointProjectID(projectID, applicationbyNodeType.getId());
        return appjointProjectID == null? projectID:appjointProjectID;
    }

    @Override
    public Long getAppjointProjectIDByApplicationName(Long projectID, String applicationName) {
        Long appjointProjectID = projectMapper.getAppjointProjectID(projectID, applicationService.getApplication(applicationName).getId());
        return appjointProjectID == null? projectID:appjointProjectID;
    }

    private DWSFlow recursiveGenerateParentFlow(DWSFlow dwsFlow,RequestDWSProject requestDWSProject) throws DSSErrorException {
        DWSFlow returnFlow = null;
        Long parentFlowID = flowService.getParentFlowID(dwsFlow.getId());
        if(parentFlowID != null){
            //???????????????????????????????????????????????????????????????????????????
            DWSFlow parentFlow = flowService.getLatestVersionFlow(parentFlowID,requestDWSProject.projectVersionId());
            parentFlow.setChildren(Arrays.asList(dwsFlow));
            returnFlow = recursiveGenerateParentFlow(parentFlow,requestDWSProject);
        }else {
            returnFlow = dwsFlow;
        }
        return returnFlow;
    }

    private void createPublishProject(String userName, Long parentFlowID, DWSFlow dwsFlowParent, Long projectVersionID) throws DSSErrorException {
       List<Long> subFlowIDS = flowMapper.selectSubFlowIDByParentFlowID(parentFlowID);
        ArrayList<DWSFlow> dwsFlows = new ArrayList<>();
        for (Long subFlowID : subFlowIDS) {
            DWSFlowVersion dwsFlowVersion = flowService.getLatestVersionByFlowIDAndProjectVersionID(subFlowID, projectVersionID);
            if (dwsFlowVersion != null) { //subFlowIDS??????flow???????????????????????????????????????????????????project????????????flows????????????
                DWSFlow dwsFlow = flowMapper.selectFlowByID(dwsFlowVersion.getFlowID());
                String json = (String) bmlService.query(userName, dwsFlowVersion.getJsonPath(), dwsFlowVersion.getVersion()).get("string");
                if (!dwsFlow.getHasSaved()) {
                    logger.info("?????????{}????????????????????????",dwsFlow.getName());
                }else if (StringUtils.isNotBlank(json)){
                    dwsFlowVersion.setJson(json);
                    dwsFlow.setLatestVersion(dwsFlowVersion);
                    createPublishProject(userName, subFlowID, dwsFlow, projectVersionID);
                    dwsFlows.add(dwsFlow);
                } else {
                    String warnMsg = String.format(DSSServerConstant.PUBLISH_FLOW_REPORT_FORMATE, dwsFlow.getName(), dwsFlowVersion.getVersion());
                    logger.info(warnMsg);
                    throw new DSSErrorException(90013, warnMsg);
                }
            }
        }
        dwsFlowParent.setChildren(dwsFlows);
    }

    /*
    ????????????
     */
    @Lock
    @Transactional(rollbackFor = {DSSErrorException.class, InterruptedException.class,AppJointErrorException.class})
    @Override
    public Long copyProject(Long projectVersionID, Long projectID, String projectName, String userName) throws DSSErrorException, InterruptedException, AppJointErrorException {
        DWSProject project = projectMapper.selectProjectByID(projectID);
        if (StringUtils.isNotEmpty(projectName)) {project.setName(projectName);}
        DWSProjectTaxonomyRelation projectTaxonomyRelation = projectTaxonomyMapper.selectProjectTaxonomyRelationByTaxonomyIdOrProjectId(projectID);
        //?????????wtss???project??????????????????projectID
        project.setUserName(userName);
        if(existSchesulis()){
            createSchedulerProject(project);
        }
        Map<Long,Long> appjointProjectIDAndAppID = createAppjointProject(project);
        Long userID = dwsUserMapper.getUserID(userName);
        //?????????dws???project?????????????????????projectID?????????????????????
        //???????????????????????????????????????id
        project.setUserID(userID);
        project.setCreateTime(new Date());
        project.setId(null);
        projectMapper.addProject(project);
        if(!appjointProjectIDAndAppID.isEmpty())projectMapper.addAccessProjectRelation(appjointProjectIDAndAppID,project.getId());
        projectTaxonomyMapper.addProjectTaxonomyRelation(project.getId(), projectTaxonomyRelation.getTaxonomyId(), userID);
        DWSProjectVersion maxVersion = projectMapper.selectLatestVersionByProjectID(projectID);
        copyProjectVersionMax(maxVersion.getId(), maxVersion, maxVersion, userName, project.getId());
        return project.getId();
    }

    private boolean existSchesulis(){
        return applicationService.getApplication("schedulis") != null;
    }


    /**
     * ????????????????????????????????????????????????,?????????????????????
     *
     * @param projectVersionID
     * @param copyVersion      ?????????????????????
     * @param WTSSprojectID    ?????????????????????,??????????????????. ???????????????????????????????????????.
     *                         ???????????????????????????,????????????null??????.
     */
    @Lock(type = LockEnum.ADD)
    @Transactional(rollbackFor = {DSSErrorException.class, InterruptedException.class})
    @Override
    public void copyProjectVersionMax(Long projectVersionID, DWSProjectVersion maxVersion, DWSProjectVersion copyVersion, String userName, Long WTSSprojectID) throws DSSErrorException, InterruptedException {
//            copy project_version
        String maxVersionNum = generateNewVersion(maxVersion.getVersion());
        if (null != WTSSprojectID) {
            copyVersion.setVersion(DSSServerConstant.DWS_PROJECT_FIRST_VERSION);
            copyVersion.setProjectID(WTSSprojectID);
        } else {
            copyVersion.setVersion(maxVersionNum);
        }
        Long userID = dwsUserMapper.getUserID(userName);
        copyVersion.setUpdatorID(userID);
        copyVersion.setUpdateTime(new Date());
        List<DWSFlowVersion> flowVersions = flowMapper.listLastFlowVersionsByProjectVersionID(copyVersion.getId())
                .stream().sorted((o1, o2) -> Integer.valueOf(o1.getFlowID().toString()) - Integer.valueOf(o2.getFlowID().toString()))
                .collect(Collectors.toList());
        Long oldProjectVersionID = copyVersion.getId();
        copyVersion.setId(null);
        projectMapper.addProjectVersion(copyVersion);
        if (copyVersion.getId() == null) {throw new DSSErrorException(90015, "????????????????????????");}
        Map<Long, Long> subAndParentFlowIDMap = new ConcurrentHashMap<>();
        // copy flow
        if (null != WTSSprojectID) {
            flowVersions.stream().forEach(f -> {
                DWSFlow flow = flowMapper.selectFlowByID(f.getFlowID());
                Long parentFlowID = flowMapper.selectParentFlowIDByFlowID(flow.getId());
                if (parentFlowID != null) {subAndParentFlowIDMap.put(flow.getId(), parentFlowID);}
            });
            for (DWSFlowVersion fv : flowVersions) {
                // ?????????????????????map???
                DWSFlow flow = flowMapper.selectFlowByID(fv.getFlowID());
                flow.setCreatorID(userID);
                flow.setName(flow.getName());
                flow.setProjectID(copyVersion.getProjectID());
                flow.setCreateTime(new Date());

                Long taxonomyID = flowTaxonomyMapper.selectTaxonomyIDByFlowID(flow.getId());
                DWSFlowTaxonomy flowTaxonomy = flowTaxonomyMapper.selectFlowTaxonomyByID(taxonomyID);
                //??????flow????????????
                fv.setOldFlowID(flow.getId());
                flow.setId(null);
                flowMapper.insertFlow(flow);
                if (null == flow.getId()) {throw new DSSErrorException(90016, "?????????????????????");}
                for (Map.Entry<Long, Long> entry : subAndParentFlowIDMap.entrySet()) {
                    if (entry.getValue().equals(fv.getFlowID())){subAndParentFlowIDMap.put(entry.getKey(), flow.getId());}
                    if (entry.getKey().equals(fv.getFlowID())){subAndParentFlowIDMap.put(flow.getId(), entry.getValue());}
                }
                if (flowTaxonomy.getProjectID() != -1 && (!flowTaxonomy.getProjectID().equals(copyVersion.getProjectID()))) {
                    flowTaxonomy.setProjectID(copyVersion.getProjectID());
                    flowTaxonomy.setCreateTime(new Date());
                    flowTaxonomy.setUpdateTime(new Date());
                    flowTaxonomy.setProjectID(copyVersion.getUpdatorID());
                    flowTaxonomyMapper.insertFlowTaxonomy(flowTaxonomy);
                }
                if (null != taxonomyID){flowTaxonomyMapper.insertFlowTaxonomyRelation(flowTaxonomy.getId(), flow.getId());}
                fv.setFlowID(flow.getId());
            }
            for (DWSFlowVersion fv : flowVersions) {
                if (subAndParentFlowIDMap.get(fv.getFlowID()) != null){flowMapper.insertFlowRelation(fv.getFlowID(), subAndParentFlowIDMap.get(fv.getFlowID()));}
            }
        }
//        copy flow_version
        if (flowVersions.size() > 0) {
            ThreadPoolTool<DWSFlowVersion> tool = new ThreadPoolTool(5, flowVersions);
            tool.setCallBack(new ThreadPoolTool.CallBack<DWSFlowVersion>() {
                @Override
                public void method(List<DWSFlowVersion> flowVersions) {
                    for (DWSFlowVersion fv : flowVersions) {
                        //            ??????????????????json????????????????????????????????????bml
                        Map<String, Object> bmlQueryMap = bmlService.download(dwsUserMapper.getuserName(fv.getUpdatorID()), fv.getJsonPath(), fv.getVersion());
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader((InputStream) bmlQueryMap.get("is")));
                        StringBuilder sb = new StringBuilder();
                        String s = null;
                        try {
                            while ((s = bufferedReader.readLine()) != null) {
                                sb.append(s);
                                sb.append("\n");
                            }
                            String json = sb.toString().trim();
                            List<Long> newflowIDs = getSubNewFlowID(subAndParentFlowIDMap, fv.getFlowID());
                            List<Long> oldFlowIDs = getOldSubFlowID(subAndParentFlowIDMap, fv.getFlowID());
                            if (oldFlowIDs != null && newflowIDs != null) {
                                logger.info("replace??????:" + json);
                                if (json != null) {
                                    for (int i = 0; i < newflowIDs.size(); i++) {
                                        json = json.replace(DSSServerConstant.EMVEDDEDFLOWID + oldFlowIDs.get(i), DSSServerConstant.EMVEDDEDFLOWID + newflowIDs.get(i));
                                    }
                                }
                                logger.info("replace??????:" + json);
                            }
                            if (json != null) {
                                InputStream in_nocode = new ByteArrayInputStream(json.getBytes());
                                Map<String, Object> bmlReturnMap = bmlService.upload(userName, in_nocode, UUID.randomUUID().toString() + ".json");
                                fv.setProjectVersionID(copyVersion.getId());
                                fv.setUpdateTime(new Date());
                                fv.setUpdatorID(copyVersion.getUpdatorID());
                                String oldFlowVersion = fv.getVersion();
                                fv.setJsonPath(bmlReturnMap.get("resourceId").toString());
                                fv.setVersion(bmlReturnMap.get("version").toString());
                                fv.setSource("Copy from Old { ProjectVersionID : " + oldProjectVersionID + " ,FlowID:" + fv.getOldFlowID() + ",Version:" + oldFlowVersion + "} to New { ProjectVersionID:" + fv.getProjectVersionID() + " ,FlowID:" + fv.getFlowID() + ",Version:" + fv.getVersion() + "}");
                                bufferedReader.close();
                                in_nocode.close();
                            }
                        } catch (IOException e) {
                            logger.error("???????????????IO??????", e);
                        }

                    }
                }
            });
            tool.excute();
        }
        if (flowVersions != null && flowVersions.size() > 0) {
            flowVersions.stream().forEach(f -> {
                logger.info("jsonPaht:{},oldeFlowID:{},newFlowID", f.getJsonPath(), f.getOldFlowID(), f.getFlowID());
            });
            flowMapper.batchInsertFlowVersion(flowVersions);
        }
    }

    private String generateNewVersion(String version){
        int next = Integer.parseInt(version.substring(1, version.length())) + 1;
        return DSSServerConstant.VERSION_PREFIX + String.format(DSSServerConstant.VERSION_FORMAT, next);
    }


    private List<Long> getOldSubFlowID(Map<Long, Long> subAndParentFlowIDMap, Long flowID) {
        List<Map.Entry<Long, Long>> collect = subAndParentFlowIDMap.entrySet().stream().filter(f -> f.getValue().equals(flowID)).collect(Collectors.toList());
        if (collect.isEmpty()) return null;
        int size = collect.size() / 2;
        ArrayList<Long> longs = new ArrayList<>();
        List<Map.Entry<Long, Long>> sortedCollections = collect.stream().sorted((o, n) -> {
            return Integer.valueOf(o.getKey().toString()) - Integer.valueOf(n.getKey().toString());
        }).collect(Collectors.toList());
        sortedCollections.subList(0, size).stream().forEach(p -> longs.add(p.getKey()));
        return longs;
        /*if(collect.isEmpty()){
            return  UUID.randomUUID().toString();
        }else {
            return  collect.get(0).getKey() - collect.get(1).getKey() <0? Constant.EMVEDDEDFLOWID +collect.get(0).getKey():Constant.EMVEDDEDFLOWID +collect.get(1).getKey();
        }*/
    }

    private List<Long> getSubNewFlowID(Map<Long, Long> subAndParentFlowIDMap, Long flowID) {
        List<Map.Entry<Long, Long>> collect = subAndParentFlowIDMap.entrySet().stream().filter(f -> f.getValue().equals(flowID)).collect(Collectors.toList());
        if (collect.isEmpty()) return null;
        int size = collect.size() / 2;
        ArrayList<Long> longs = new ArrayList<>();
        List<Map.Entry<Long, Long>> sortedCollections = collect.stream().sorted((o, n) -> {
            return Integer.valueOf(n.getKey().toString()) - Integer.valueOf(o.getKey().toString());
        }).collect(Collectors.toList());
        sortedCollections.subList(0, size).stream().forEach(p -> longs.add(p.getKey()));
        return longs.stream().sorted((o1, o2) -> {
            return Integer.valueOf(o1.toString()) - Integer.valueOf(o2.toString());
        }).collect(Collectors.toList());
    }

}
