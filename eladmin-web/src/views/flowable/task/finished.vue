<template>
  <div class="app-container">
    <div class="head-container">
      <!-- 刷新按钮 -->
      <el-button class="filter-item" size="mini" type="success" icon="el-icon-refresh" @click="toQuery">刷新</el-button>
    </div>
    <!--表格渲染-->
    <el-table v-loading="loading" :data="data" size="small" style="width: 100%;">
      <el-table-column prop="processName" label="流程实例名称" />
      <el-table-column prop="createTime" label="接收时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="完成时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时">
        <template slot-scope="scope">
          <span>{{ formatDuration(scope.row.duration) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.processStatus === '已拒绝' ? 'danger' : (scope.row.processStatus === '已结束' ? 'info' : 'success')">
            {{ scope.row.processStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100px" align="center">
        <template slot-scope="scope">
          <el-button size="mini" plain type="primary" @click="handleTrack(scope.row)">查看详情</el-button>
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
      <el-row :gutter="20">
        <el-col :span="16">
          <div style="font-weight: bold; margin-bottom: 10px;">流程图</div>
          <div id="bpmnCanvas" style="height: 500px; width: 100%; border: 1px solid #ddd;" />
        </el-col>
        <el-col :span="8">
          <div style="font-weight: bold; margin-bottom: 10px;">审批流转历史</div>
          <div style="height: 500px; overflow-y: auto; border: 1px solid #ddd; padding: 10px;">
            <el-timeline>
              <el-timeline-item
                v-for="(activity, index) in taskHistory"
                v-if="activity.endTime && activity.activityType === 'userTask'"
                :key="index"
                :timestamp="parseTime(activity.endTime)"
                placement="top"
              >
                <el-card :body-style="{ padding: '10px' }" shadow="hover">
                  <h4 style="margin: 0 0 5px 0;">{{ activity.activityName }}</h4>
                  <p style="margin: 0; font-size: 13px; color: #666;">审批人: {{ activity.assigneeName || activity.assignee || activity.startUserName }}</p>
                  <p style="margin: 5px 0 0 0; font-size: 13px;">
                    意见:
                    <!-- Special handling for start event or first user task if needed -->
                    <span v-if="activity.activityType === 'startEvent'">提交申请</span>
                    <span v-else-if="activity.comments && activity.comments.length > 0">
                      <span v-for="(comment, cIndex) in activity.comments" :key="cIndex">{{ comment }} </span>
                    </span>
                    <span v-else>无</span>
                  </p>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <div v-if="!taskHistory || taskHistory.length === 0" style="text-align: center; color: #999; margin-top: 20px;">暂无历史</div>
          </div>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script>
import { getFinishedTasks, getHistory } from '@/api/flowable'
import { readXml } from '@/api/flowable/definition'
import { parseTime } from '@/utils/index'
import BpmnViewer from 'bpmn-js/lib/Viewer'

export default {
  name: 'FlowableFinishedTask',
  data() {
    return {
      loading: false,
      data: [],
      page: 0,
      size: 10,
      total: 0,
      trackVisible: false,
      trackTitle: '',
      trackProcessInstanceId: '',
      trackDefinitionId: '',
      bpmnViewer: null,
      taskHistory: []
    }
  },
  created() {
    this.toQuery()
  },
  methods: {
    parseTime,
    formatDuration(ms) {
      if (!ms) return ''

      const seconds = Math.floor(ms / 1000)
      const minutes = Math.floor(seconds / 60)
      const hours = Math.floor(minutes / 60)
      const days = Math.floor(hours / 24)

      if (days > 0) {
        return days + '天' + (hours % 24) + '小时'
      } else if (hours > 0) {
        return hours + '小时' + (minutes % 60) + '分'
      } else if (minutes > 0) {
        return minutes + '分' + (seconds % 60) + '秒'
      } else {
        return seconds + '秒'
      }
    },
    toQuery() {
      this.loading = true
      getFinishedTasks({ page: this.page, size: this.size }).then(res => {
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
    renderDiagram() {
      this.$nextTick(() => {
        if (this.bpmnViewer) {
          this.bpmnViewer.destroy()
        }
        this.bpmnViewer = new BpmnViewer({
          container: '#bpmnCanvas',
          height: 500
        })

        readXml(this.trackDefinitionId).then(xml => {
          this.bpmnViewer.importXML(xml).then(() => {
            const canvas = this.bpmnViewer.get('canvas')
            const overlays = this.bpmnViewer.get('overlays')

            canvas.zoom('fit-viewport')

            getHistory(this.trackProcessInstanceId).then(history => {
              this.taskHistory = history
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
                if (!activity.endTime) {
                  canvas.addMarker(activity.activityId, 'highlight-active')
                  if (activity.assignee || activity.assigneeName) {
                    const html = `<div class="diagram-note active">
                                           待办: ${activity.assigneeName || activity.assignee}
                                       </div>`
                    overlays.add(activity.activityId, {
                      position: { top: 87, left: 0 },
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
    background-color: rgba(24, 144, 255, 0.7);
}
.highlight-rejected:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: red !important;
    fill: rgba(255, 0, 0, 0.2) !important;
}
.diagram-note.rejected {
    background-color: rgba(255, 0, 0, 0.7);
}
.highlight-skipped:not(.djs-connection) .djs-visual > :nth-child(1) {
    stroke: #999 !important;
    fill: rgba(153, 153, 153, 0.2) !important;
}
</style>
