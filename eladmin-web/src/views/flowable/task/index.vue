<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <!-- 刷新按钮 -->
      <el-button class="filter-item" size="mini" type="success" icon="el-icon-refresh" @click="toQuery">刷新</el-button>
    </div>
    <!--表格渲染-->
    <el-table v-loading="loading" :data="data" style="width: 100%;">
      <el-table-column prop="processName" label="流程实例名称" />
      <el-table-column prop="name" label="当前节点" />
      <el-table-column prop="createTime" label="创建时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.processStatus === '已拒绝' ? 'danger' : (scope.row.processStatus === '已结束' ? 'info' : 'success')">
            {{ scope.row.processStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200px" align="center">
        <template slot-scope="scope">
          <el-button size="mini" plain type="primary" @click="handleTrack(scope.row)">查看详情</el-button>
          <el-button size="mini" type="primary" @click="handleComplete(scope.row)">办理</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!--分页组件-->
    <el-pagination
      :total="total"
      :current-page="page + 1"
      style="margin-top: 8px;"
      layout="total, prev, pager, next, sizes"
      @size-change="sizeChange"
      @current-change="pageChange"
    />
    <!-- 流程追踪图弹窗 -->
    <el-dialog :title="trackTitle" :visible.sync="trackVisible" width="90%" @opened="renderDiagram">
      <div id="bpmnCanvas" style="height: 500px; width: 100%;" />
    </el-dialog>

    <!-- 办理弹窗 -->
    <el-dialog title="任务办理" :visible.sync="handleVisible" width="600px">
      <el-form label-width="100px">
        <!-- Process Description -->
        <el-form-item v-if="handleProcessDescription" label="任务描述">
          <div style="background: #f5f7fa; padding: 10px; border-radius: 4px; white-space: pre-wrap;">{{ handleProcessDescription }}</div>
        </el-form-item>
        <!-- History Comments -->
        <el-form-item label="审批历史">
          <el-timeline>
            <el-timeline-item
              v-for="(activity, index) in handleHistory"
              v-if="activity.endTime && activity.activityType === 'userTask'"
              :key="index"
              :timestamp="parseTime(activity.endTime)"
              placement="top"
            >
              <el-card :body-style="{ padding: '10px' }" shadow="hover">
                <h4 style="margin: 0 0 5px 0;">{{ activity.activityName }}</h4>
                <p style="margin: 0; font-size: 13px; color: #666;">审批人: {{ activity.assigneeName || activity.assignee }}</p>
                <p style="margin: 5px 0 0 0; font-size: 13px;">
                  意见:
                  <span v-if="index === 0">提交申请</span>
                  <span v-else-if="activity.comments && activity.comments.length > 0">
                    <span v-for="(comment, cIndex) in activity.comments" :key="cIndex">{{ comment }} </span>
                  </span>
                  <span v-else>无</span>
                </p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <div v-if="!handleHistory || handleHistory.length === 0">暂无历史</div>
        </el-form-item>

        <el-form-item label="审批意见">
          <el-input
            v-model="handleComment"
            type="textarea"
            placeholder="请输入审批意见 (可选)"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="danger" @click="rejectHandling">不同意</el-button>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandling">同意</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMyTasks, completeTask, getHistory, getProcessVariables, rejectTask } from '@/api/flowable'
import { readXml } from '@/api/flowable/definition'
import { parseTime } from '@/utils/index'
import BpmnViewer from 'bpmn-js/lib/Viewer'

export default {
  name: 'FlowableTask',
  data() {
    return {
      loading: false,
      data: [],
      page: 0,
      size: 10,
      total: 0,

      // Track
      trackVisible: false,
      trackTitle: '',
      trackProcessInstanceId: '',
      trackDefinitionId: '',
      bpmnViewer: null,

      // Handle
      handleVisible: false,
      handleTaskId: '',
      handleProcessInstanceId: '',
      handleComment: '',
      handleProcessDescription: '',
      handleHistory: []
    }
  },

  created() {
    this.toQuery()
  },
  methods: {
    parseTime,
    toQuery() {
      this.loading = true
      getMyTasks({ page: this.page, size: this.size }).then(res => {
        this.data = res
        this.total = res.length
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    pageChange(e) {
      this.page = e - 1
      this.toQuery()
    },
    sizeChange(e) {
      this.page = 0
      this.size = e
      this.toQuery()
    },
    handleTrack(row) {
      this.trackTitle = '流程详情 - ' + (row.processName || row.name)
      this.trackProcessInstanceId = row.processInstanceId
      this.trackDefinitionId = row.processDefinitionId
      this.trackVisible = true
    },
    handleComplete(row) {
      this.handleTaskId = row.id
      this.handleProcessInstanceId = row.processInstanceId
      this.handleComment = ''
      this.handleProcessDescription = ''
      this.handleHistory = []

      // Fetch variables and history
      this.loading = true // optionally create separate loading for dialog

      Promise.all([
        getProcessVariables(row.processInstanceId),
        getHistory(row.processInstanceId)
      ]).then(([vars, history]) => {
        this.handleProcessDescription = vars.description || '无描述'
        // Filter completed user tasks for history timeline
        this.handleHistory = history.filter(h => h.activityType === 'userTask' && h.endTime)

        this.handleVisible = true
        this.loading = false
      }).catch(() => {
        this.loading = false
        this.$message.error('获取任务详情失败')
      })
    },
    submitHandling() {
      completeTask({
        taskId: this.handleTaskId,
        variables: {
          comment: this.handleComment // Pass comment as variable if backend handles it separately or reuse convention
        }
      }).then(() => {
        this.$notify({
          title: '成功',
          message: '已同意',
          type: 'success',
          duration: 2000
        })
        this.handleVisible = false
        this.toQuery()
      })
    },
    rejectHandling() {
      if (!this.handleComment) {
        this.$message.warning('请填写不同意的原因')
        return
      }
      this.$confirm('确定要拒绝该任务并结束流程吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        rejectTask({
          taskId: this.handleTaskId,
          comment: this.handleComment
        }).then(() => {
          this.$notify({
            title: '成功',
            message: '已拒绝并结束流程',
            type: 'success',
            duration: 2000
          })
          this.handleVisible = false
          this.toQuery()
        })
      })
    },
    renderDiagram() {
      this.$nextTick(() => {
        if (!this.bpmnViewer) {
          this.bpmnViewer = new BpmnViewer({
            container: '#bpmnCanvas',
            height: 500
          })
        }

        // 1. Get XML
        readXml(this.trackDefinitionId).then(xml => {
          this.bpmnViewer.importXML(xml).then(() => {
            const canvas = this.bpmnViewer.get('canvas')
            const overlays = this.bpmnViewer.get('overlays')

            // Zoom to fit
            canvas.zoom('fit-viewport')

            // 2. Get History
            getHistory(this.trackProcessInstanceId).then(history => {
              let hasRejection = false
              // Set to track visited nodes
              const visitedNodeIds = new Set()
              history.forEach(activity => {
                visitedNodeIds.add(activity.activityId)
                if (activity.activityType === 'userTask' || activity.activityType === 'startEvent' || activity.activityType === 'endEvent') {
                  // Check for deleteReason to determine rejection
                  if (activity.deleteReason) {
                    hasRejection = true
                    canvas.addMarker(activity.activityId, 'highlight-rejected')
                  } else {
                    canvas.addMarker(activity.activityId, 'highlight-completed')
                  }

                  if (activity.activityType === 'userTask' && activity.endTime) {
                    // Formatting helper for note
                    let noteClass = 'diagram-note'
                    if (activity.deleteReason) {
                      noteClass += ' rejected'
                    }

                    const html = `<div class="${noteClass}">
                                           ${activity.assigneeName ? '审批人:' + activity.assigneeName : ''}<br/>
                                           ${activity.endTime ? parseTime(activity.endTime) : ''}
                                       </div>`
                    overlays.add(activity.activityId, {
                      position: { top: 87, left: 0 },
                      html: html
                    })
                  }
                }
                // Highlight current tasks (no endTime)
                if (!activity.endTime) {
                  canvas.addMarker(activity.activityId, 'highlight-active')
                  // Support assignee OR assigneeName (for Candidate Groups)
                  if (activity.assignee || activity.assigneeName) {
                    const html = `<div class="diagram-note active">
                                           待办: ${activity.assigneeName || activity.assignee}
                                       </div>`
                    overlays.add(activity.activityId, {
                      position: { top: 87, left: 0 }, // Adjust position to avoid overlap if needed
                      html: html
                    })
                  }
                }
              })

              // If rejected, gray out subsequent (unvisited) user tasks
              if (hasRejection) {
                const elementRegistry = this.bpmnViewer.get('elementRegistry')
                const allElements = elementRegistry.getAll()
                allElements.forEach(element => {
                  if (element.type === 'bpmn:UserTask' && !visitedNodeIds.has(element.id)) {
                    canvas.addMarker(element.id, 'highlight-skipped')
                  }
                })
              }
            })
          })
        })
      })
    }
  }
}
</script>

<style>
.highlight-completed:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: green !important;
    fill: rgba(0, 128, 0, 0.2) !important;
}
.highlight-active:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: #1890ff !important;
    fill: rgba(24, 144, 255, 0.2) !important;
}
.highlight-rejected:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: red !important;
    fill: rgba(255, 0, 0, 0.2) !important;
}
.highlight-skipped:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: #999 !important;
    fill: rgba(153, 153, 153, 0.2) !important;
}
.diagram-note {
    background-color: rgba(66, 180, 21, 0.7);
    color: white;
    border-radius: 5px;
    font-size: 12px;
    padding: 5px;
    font-family: Arial;
    min-width: 30px;
    white-space: nowrap;
    text-align: left;
}
.diagram-note.active {
    background-color: #1890ff;
    opacity: 0.8;
}
.diagram-note.rejected {
    background-color: rgba(255, 0, 0, 0.7);
}
</style>

<style scoped>

</style>
