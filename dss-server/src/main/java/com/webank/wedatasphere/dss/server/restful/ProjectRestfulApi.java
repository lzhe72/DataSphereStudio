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

package com.webank.wedatasphere.dss.server.restful;


import com.webank.wedatasphere.dss.appjoint.exception.AppJointErrorException;
import com.webank.wedatasphere.dss.common.entity.project.DWSProject;
import com.webank.wedatasphere.dss.common.entity.project.DWSProjectVersion;
import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.server.dao.DWSUserMapper;
import com.webank.wedatasphere.dss.server.dao.ProjectMapper;
import com.webank.wedatasphere.dss.server.entity.ApplicationArea;
import com.webank.wedatasphere.dss.server.publish.*;
import com.webank.wedatasphere.dss.server.service.DWSProjectService;
import com.webank.wedatasphere.dss.server.service.DWSProjectTaxonomyService;
import com.webank.wedatasphere.dss.server.service.DWSUserService;
import com.webank.wedatasphere.linkis.server.Message;
import com.webank.wedatasphere.linkis.server.security.SecurityFilter;
import org.codehaus.jackson.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;


@Component
@Path("/dss")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectRestfulApi {

    @Autowired
    private DWSProjectTaxonomyService projectTaxonomyService;
    @Autowired
    private DWSProjectService projectService;
    @Autowired
    private DWSUserService dwsUserService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private PublishJobFactory publishJobFactory;
    @Autowired
    private PublishManager publishManager;

    @Autowired
    private DWSUserMapper dwsUserMapper;

    @GET
    @Path("/listAllProjectVersions")
    public Response listAllVersions(@Context HttpServletRequest req, @QueryParam("id") Long projectID) {
        List<DWSProjectVersion> versions = projectService.listAllProjectVersions(projectID);
        return Message.messageToResponse(Message.ok().data("versions", versions));
    }

    @GET
    @Path("/listApplicationAreas")
    public Response listApplicationAreas(@Context HttpServletRequest req) {
        String header = req.getHeader("Content-language").trim();
        ApplicationArea[] applicationAreas = ApplicationArea.values();
        List<String> areas = new ArrayList<>();
        Arrays.stream(applicationAreas).forEach(item ->{
            if ("zh-CN".equals(header)){
                areas.add( item.getName());
            }else {
                areas.add(item.getEnName());
            }
        });
        return Message.messageToResponse(Message.ok().data("applicationAreas", areas));
    }

    @POST
    @Path("/addProject")
    public Response addProject(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException,AppJointErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        String name = json.get("name").getTextValue();
        String description = json.get("description").getTextValue();
        Long taxonomyID = json.get("taxonomyID") == null? null:json.get("taxonomyID").getLongValue();
        String product = json.get("product").getTextValue();
        Integer applicationArea = json.get("applicationArea").getIntValue();
        String business = json.get("business").getTextValue();
        // TODO: 2019/5/16 ??????????????????????????????
        projectService.addProject(userName, name, description, taxonomyID,product,applicationArea,business);
        return Message.messageToResponse(Message.ok());
    }

    @POST
    @Path("/updateProject")
    public Response updateProject(@Context HttpServletRequest req, JsonNode json) throws AppJointErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        String name = json.get("name") == null ? null : json.get("name").getTextValue();
        String description = json.get("description") == null ? null : json.get("description").getTextValue();
        long projectID = json.get("id").getLongValue();
        Long taxonomyID = json.get("taxonomyID") == null ? null : json.get("taxonomyID").getLongValue();
        String product = json.get("product").getTextValue();
        Integer applicationArea = json.get("applicationArea").getIntValue();
        String business = json.get("business").getTextValue();
        //????????????????????????
        projectService.updateProject(projectID, name, description, userName , product ,applicationArea ,business);
        projectTaxonomyService.updateProjectTaxonomyRelation(projectID, taxonomyID);
        return Message.messageToResponse(Message.ok());
    }

    @POST
    @Path("/deleteProject")
    public Response deleteProject(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        Long projectID = json.get("id").getLongValue();
        Boolean sure = json.get("sure") != null&&json.get("sure").getBooleanValue();
        Boolean ifDelScheduler = json.get("ifDelScheduler") != null &&json.get("ifDelScheduler").getBooleanValue();
        if (projectService.isPublished(projectID) && !sure) {
            return Message.messageToResponse(Message.ok().data("warmMsg", "????????????????????????????????????"));
        }
        // TODO: 2019/5/23 ??????projectID???-1
        projectService.deleteProject(projectID, userName,ifDelScheduler);
        return Message.messageToResponse(Message.ok());
    }

    @POST
    @Path("/copyProjectAndFlow")
    public Response copyProject(@Context HttpServletRequest req, JsonNode json) throws InterruptedException, DSSErrorException, AppJointErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        Long projectID = json.get("projectID").getLongValue();
        String projectName = json.get("projectName") == null ? null : json.get("projectName").getTextValue();
        DWSProjectVersion maxVersion = projectMapper.selectLatestVersionByProjectID(projectID);
        projectService.copyProject( maxVersion.getId(),projectID, projectName, userName);
        return Message.messageToResponse(Message.ok());
    }

    @POST
    @Path("/copyVersionAndFlow")
    public Response copyProjectVersion(@Context HttpServletRequest req, JsonNode json) throws InterruptedException, DSSErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        Long copyprojectVersionID = json.get("projectVersionID").getLongValue();
        DWSProjectVersion currentVersion = projectMapper.selectProjectVersionByID(copyprojectVersionID);
        DWSProjectVersion maxVersion = projectMapper.selectLatestVersionByProjectID(currentVersion.getProjectID());

        projectService.copyProjectVersionMax( maxVersion.getId(), maxVersion,currentVersion,userName,null);
        return Message.messageToResponse(Message.ok());
    }

    @POST
    @Path("/publish")
    public Response publish(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        Long projectID = json.get("id").getLongValue();
        String comment = json.get("comment").getTextValue();
        DWSProject latestVersionProject = projectService.getLatestVersionProject(projectID);
        publishManager.addPublishCache(latestVersionProject.getLatestVersion().getId(),dwsUserService.getUserID(userName),comment);
        PublishSubmitJob job = publishJobFactory.createSubmitPublishJob(latestVersionProject.getLatestVersion().getId(), userName, comment);
        Future<?> submit = PublishThreadPool.get().submit(job);
        PublishSubmitJobDeamon deamon = publishJobFactory.createSubmitPublishJobDeamon(submit, job);
        PublishThreadPool.getDeamon().execute(deamon);
        return Message.messageToResponse(Message.ok());
    }

    @GET
    @Path("/publishQuery")
    public Response publishQuery(@Context HttpServletRequest req, @QueryParam("projectVersionID") Long projectVersionID) {
        PublishProjectCache cache = publishManager.getPublishCache(projectVersionID);
        return Message.messageToResponse(Message.ok().data("info",cache));
    }

    @GET
    @Path("/getAppJointProjectID")
    public Response getAppjointProjectID(@Context HttpServletRequest req, @QueryParam("projectID") Long projectID,@QueryParam("nodeType") String nodeType) {
        Long appJointProjectID =  projectService.getAppjointProjectID(projectID,nodeType);
        return Message.messageToResponse(Message.ok().data("appJointProjectID",appJointProjectID));
    }

    @GET
    @Path("/getAppjointProjectIDByApplicationName")
    public Response listAppJointProjectID(@Context HttpServletRequest req, @QueryParam("projectID") Long projectID,@QueryParam("applicationName") String applicationName) {
        Long appJointProjectID = projectService.getAppjointProjectIDByApplicationName(projectID,applicationName);
        return Message.messageToResponse(Message.ok().data("appJointProjectID",appJointProjectID));
    }

}
