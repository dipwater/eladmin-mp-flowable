<template>
  <div class="app-container">
    <!-- Header Actions -->
    <div class="head-container">
      <el-button class="filter-item" size="mini" type="success" icon="el-icon-refresh" @click="toQuery">刷新</el-button>
    </div>

    <!-- Table -->
    <el-table v-loading="loading" :data="data" size="small" style="width: 100%;">
      <el-table-column prop="name" label="实例名称" show-overflow-tooltip />
      <el-table-column prop="processDefinitionName" label="流程名称" />
      <el-table-column prop="processDefinitionVersion" label="版本" width="80px" align="center" />
      <el-table-column prop="startUserName" label="发起人" />
      <el-table-column prop="startTime" label="开始时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="结束时间">
        <template slot-scope="scope">
          <span>{{ scope.row.endTime ? parseTime(scope.row.endTime) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.endTime" type="info">已结束</el-tag>
          <el-tag v-else :type="scope.row.suspended ? 'danger' : 'success'">{{ scope.row.suspended ? '已挂起' : '进行中' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200px" align="center">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            @click="handleTrack(scope.row)"
          >详情</el-button>
          <el-button
            v-if="scope.row.suspended && !scope.row.endTime"
            size="mini"
            type="text"
            @click="changeState(scope.row, 1)"
          >激活</el-button>
          <el-button
            v-else-if="!scope.row.endTime"
            size="mini"
            type="text"
            @click="changeState(scope.row, 2)"
          >挂起</el-button>
          <el-popover
            :ref="scope.row.id"
            placement="top"
            width="200"
          >
            <p>确认删除（终止）此实例吗？</p>
            <el-input v-model="deleteReason" placeholder="请输入删除原因" size="mini" style="margin-bottom: 10px;" />
            <div style="text-align: right; margin: 0">
              <el-button size="mini" type="text" @click="$refs[scope.row.id].doClose()">取消</el-button>
              <el-button :loading="delLoading" type="primary" size="mini" @click="deleteInstance(scope.row.id)">确认</el-button>
            </div>
            <el-button slot="reference" type="text" size="mini" style="color: red; margin-left: 10px;">删除</el-button>
          </el-popover>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <el-pagination
      :total="total"
      :current-page="page + 1"
      style="margin-top: 8px;"
      layout="total, prev, pager, next, sizes"
      @size-change="sizeChange"
      @current-change="pageChange"
    />

    <!-- Track Dialog -->
    <el-dialog :title="trackTitle" :visible.sync="trackVisible" width="90%" @opened="renderDiagram">
      <div id="bpmnCanvas" style="height: 500px; width: 100%;" />
    </el-dialog>
  </div>
</template>

<script>
import { getInstances, deleteInstance, updateInstanceState } from '@/api/flowable/instance'
import { getHistory } from '@/api/flowable'
import { readXml } from '@/api/flowable/definition'
import { parseTime } from '@/utils/index'
import BpmnViewer from 'bpmn-js/lib/Viewer'

export default {
  name: 'Instance',
  data() {
    return {
      loading: false,
      delLoading: false,
      data: [],
      page: 0,
      size: 10,
      total: 0,
      deleteReason: '',

      // Track
      trackVisible: false,
      trackTitle: '',
      trackProcessInstanceId: '',
      trackDefinitionId: '',
      bpmnViewer: null
    }
  },
  created() {
    this.toQuery()
  },
  methods: {
    parseTime,
    toQuery() {
      this.loading = true
      getInstances({ page: this.page, size: this.size }).then(res => {
        this.data = res.content || res
        this.total = res.totalElements || res.length
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
    changeState(row, state) {
      this.loading = true
      updateInstanceState(row.id, state).then(() => {
        this.$notify({
          title: '成功',
          message: '状态已更新',
          type: 'success',
          duration: 2500
        })
        this.toQuery()
      }).catch(err => {
        this.loading = false
        console.error(err)
      })
    },
    deleteInstance(id) {
      this.delLoading = true
      deleteInstance(id, this.deleteReason).then(() => {
        this.delLoading = false
        this.$refs[id].doClose()
        this.deleteReason = '' // reset
        this.toQuery()
        this.$notify({
          title: '成功',
          message: '删除成功',
          type: 'success',
          duration: 2500
        })
      }).catch(err => {
        this.delLoading = false
        this.$refs[id].doClose()
        console.error(err)
      })
    },
    handleTrack(row) {
      this.trackTitle = '流程详情 - ' + (row.name)
      this.trackProcessInstanceId = row.id // Instance ID
      this.trackDefinitionId = row.processDefinitionId
      this.trackVisible = true
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
                  // Check for deleteReason
                  if (activity.deleteReason) {
                    hasRejection = true
                    canvas.addMarker(activity.activityId, 'highlight-rejected')
                  } else {
                    canvas.addMarker(activity.activityId, 'highlight-completed')
                  }

                  if (activity.activityType === 'userTask' && activity.endTime) {
                    // note
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
                // Highlight current tasks
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

              // If rejected, gray out subsequent
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
