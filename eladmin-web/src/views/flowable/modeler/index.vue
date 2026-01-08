<template>
  <div class="app-container">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #ccc; padding-bottom: 10px; margin-bottom: 10px;">
      <div style="display: flex; gap: 10px;">
        <el-input v-model="info.name" placeholder="请输入流程名称" size="small" style="width: 200px;" @input="updateProcessInfo" />
        <el-input v-model="info.key" placeholder="请输入流程标识(Key)" size="small" style="width: 200px;" @input="updateProcessInfo" />
      </div>
      <div>
        <el-button type="primary" size="small" icon="el-icon-check" @click="handleSave">保存</el-button>
        <el-button type="primary" size="small" icon="el-icon-upload" @click="handleDeploy">发布</el-button>
        <el-button type="success" size="small" icon="el-icon-download" @click="download">下载XML</el-button>
        <el-button size="small" icon="el-icon-back" @click="back">返回</el-button>
      </div>
    </div>

    <div class="modeler-container" style="display: flex; height: calc(100vh - 150px);">
      <div ref="canvas" class="canvas" />
      <div v-if="selectedElement" class="properties-panel">
        <div class="panel-header">属性配置</div>
        <el-form label-position="top" size="small">
          <el-form-item label="ID">
            <el-input v-model="elementProps.id" disabled />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="elementProps.name" @change="updateName" />
          </el-form-item>

          <div v-if="elementType === 'bpmn:Task'">
            <el-alert title="提示" type="info" :closable="false" show-icon>
              请点击左侧扳手图标<br>将类型切换为"用户任务"<br>即可配置任务分配
            </el-alert>
          </div>

          <!-- User Task Specific Properties -->
          <div v-if="isUserTask">
            <el-divider>任务分配</el-divider>
            <el-form-item label="分配类型">
              <el-radio-group v-model="assignType">
                <el-radio label="assignee">指定人员</el-radio>
                <el-radio label="candidateGroups">候选组(岗位)</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="assignType === 'assignee'" label="处理人">
              <treeselect v-model="elementProps.assignee" :options="users" placeholder="选择处理人" @input="updateAssignee" />
            </el-form-item>

            <el-form-item v-if="assignType === 'candidateGroups'" label="候选组">
              <el-select v-model="elementProps.candidateGroups" placeholder="选择岗位" style="width: 100%;" @change="updateCandidateGroups">
                <el-option v-for="item in jobs" :key="item.id || item.jobId" :label="item.name" :value="String(item.id || item.jobId)" />
              </el-select>
            </el-form-item>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script>
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { deploy, readXml, readModelXml } from '@/api/flowable/definition'
// Define flowableModdle directly here to avoid module resolution/json errors
const flowableModdle = {
  'name': 'Flowable',
  'uri': 'http://flowable.org/bpmn',
  'prefix': 'flowable',
  'xml': {
    'tagAlias': 'lowerCase'
  },
  'types': [
    {
      'name': 'Process',
      'extends': [
        'bpmn:Process'
      ],
      'properties': [
        {
          'name': 'candidateStarterGroups',
          'type': 'String',
          'isAttr': true
        },
        {
          'name': 'candidateStarterUsers',
          'type': 'String',
          'isAttr': true
        }
      ]
    },
    {
      'name': 'UserTask',
      'extends': [
        'bpmn:UserTask'
      ],
      'properties': [
        {
          'name': 'assignee',
          'type': 'String',
          'isAttr': true
        },
        {
          'name': 'candidateUsers',
          'type': 'String',
          'isAttr': true
        },
        {
          'name': 'candidateGroups',
          'type': 'String',
          'isAttr': true
        },
        {
          'name': 'dueDate',
          'type': 'String',
          'isAttr': true
        }
      ]
    },
    {
      'name': 'StartEvent',
      'extends': [
        'bpmn:StartEvent'
      ],
      'properties': [
        {
          'name': 'initiator',
          'type': 'String',
          'isAttr': true
        },
        {
          'name': 'formKey',
          'type': 'String',
          'isAttr': true
        }
      ]
    }
  ]
}

import { getAllJob } from '@/api/system/job'
import { getAllUser } from '@/api/system/user'
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'

export default {
  name: 'Modeler',
  components: { Treeselect },
  data() {
    return {
      bpmnModeler: null,
      loading: false,
      info: {
        name: '',
        key: ''
      },
      selectedElement: null,
      elementProps: {
        id: '',
        name: '',
        assignee: null,
        candidateGroups: null
      },
      assignType: 'assignee',
      users: [],
      jobs: [],
      elementType: ''
    }
  },
  computed: {
    isUserTask() {
      return this.elementType === 'bpmn:UserTask'
    }
  },
  mounted() {
    this.init()
    this.loadData()
  },
  methods: {
    init() {
      // Initialize Modeler
      this.bpmnModeler = new BpmnModeler({
        container: this.$refs.canvas,
        moddleExtensions: {
          flowable: flowableModdle
        }
      })

      // Add selection listener
      this.bpmnModeler.on('selection.changed', e => {
        const selection = e.newSelection
        if (selection.length === 1) {
          this.selectedElement = selection[0]
          this.loadProperties(this.selectedElement)
        } else {
          this.selectedElement = null
        }
      })

      // Add element changed listener to update properties panel if type changes (e.g. morph)
      this.bpmnModeler.on('element.changed', e => {
        if (e.element === this.selectedElement) {
          this.loadProperties(this.selectedElement)
        }
      })

      // Auto-morph 'bpmn:Task' to 'bpmn:UserTask' on creation (works for both palette and context pad)
      this.bpmnModeler.on('shape.added', e => {
        const element = e.element
        if (element.type === 'bpmn:Task') {
          // Use setTimeout to skip the current event loop tick, ensuring the shape is fully initialized
          setTimeout(() => {
            const replace = this.bpmnModeler.get('bpmnReplace')
            replace.replaceElement(element, { type: 'bpmn:UserTask' })
          }, 0)
        }
      })

      // Check if editing an existing model
      const modelId = this.$route.query.modelId
      if (modelId) {
        this.modelId = modelId // Store modelId
        this.loadModel(modelId)
      } else {
        const deployId = this.$route.query.deployId
        if (deployId) {
          this.loadProcess(deployId)
        } else {
          // Create a default empty new diagram
          this.createNewDiagram()
        }
      }
    },
    loadModel(id) {
      this.loading = true
      readModelXml(id).then(xml => {
        this.bpmnModeler.importXML(xml).then(() => {
          this.loading = false
          // Extract info from loaded xml
          const rootElement = this.bpmnModeler.get('canvas').getRootElement()
          const businessObject = rootElement.businessObject
          this.info.key = businessObject.id
          this.info.name = businessObject.name

          // If it's a collaboration (pool), we might need to find the process inside
          if (!this.info.key && businessObject.$type === 'bpmn:Collaboration') {
            const definitions = this.bpmnModeler.getDefinitions()
            const process = definitions.rootElements.find(e => e.$type === 'bpmn:Process')
            if (process) {
              this.info.key = process.id
              this.info.name = process.name
            }
          }
        }).catch(err => {
          this.loading = false
          console.error(err)
          this.$message.error('加载模型失败')
        })
      }).catch(() => {
        this.loading = false
      })
    },
    loadProcess(id) {
      this.loading = true
      readXml(id).then(xml => {
        this.bpmnModeler.importXML(xml).then(() => {
          this.loading = false
          // Extract info from loaded xml
          const rootElement = this.bpmnModeler.get('canvas').getRootElement()
          const businessObject = rootElement.businessObject
          this.info.key = businessObject.id
          this.info.name = businessObject.name

          // If it's a collaboration (pool), we might need to find the process inside
          if (!this.info.key && businessObject.$type === 'bpmn:Collaboration') {
            const definitions = this.bpmnModeler.getDefinitions()
            const process = definitions.rootElements.find(e => e.$type === 'bpmn:Process')
            if (process) {
              this.info.key = process.id
              this.info.name = process.name
            }
          }
        }).catch(err => {
          this.loading = false
          console.error(err)
          this.$message.error('加载流程失败')
        })
      }).catch(() => {
        this.loading = false
      })
    },
    loadData() {
      getAllJob().then(res => {
        this.jobs = res.content
      })
      getAllUser().then(res => {
        this.users = (res.content || res).map(u => ({ id: u.username, label: u.nickName }))
        // Add Initiator option
        this.users.unshift({ id: '${initiator}', label: '流程发起人' })
      })
    },
    loadProperties(element) {
      if (!element) return
      const bo = element.businessObject

      this.elementType = element.type // Store type in data for reactivity
      this.elementProps = {
        id: bo.id,
        name: bo.name,
        // Try direct property first, then 'flowable:' prefixed getter, then raw getter
        assignee: bo.assignee || (bo.get && bo.get('flowable:assignee')),
        candidateGroups: bo.candidateGroups || (bo.get && bo.get('flowable:candidateGroups'))
      }
      if (this.elementProps.candidateGroups) {
        this.assignType = 'candidateGroups'
      } else {
        this.assignType = 'assignee'
        // Default to assignee if neither is set, but ensure UI reflects it
        if (!this.elementProps.assignee && !this.elementProps.candidateGroups) {
          this.assignType = 'assignee'
        }
      }
    },
    updateName(val) {
      const modeling = this.bpmnModeler.get('modeling')
      modeling.updateProperties(this.selectedElement, {
        name: val
      })
    },
    updateAssignee(val) {
      const modeling = this.bpmnModeler.get('modeling')
      modeling.updateProperties(this.selectedElement, {
        assignee: val,
        candidateGroups: undefined // clear other
      })
    },
    updateCandidateGroups(val) {
      const modeling = this.bpmnModeler.get('modeling')
      // Map dept ID to candidateGroups string
      modeling.updateProperties(this.selectedElement, {
        candidateGroups: val ? val.toString() : undefined,
        assignee: undefined // clear other
      })
    },
    createNewDiagram() {
      const diagramXML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" 
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" 
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC" 
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI" 
             xmlns:flowable="http://flowable.org/bpmn" 
             targetNamespace="http://www.flowable.org/processdef">
  <process id="${this.info.key}" name="${this.info.name}" isExecutable="true">
    <startEvent id="startEvent1" flowable:initiator="initiator" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${this.info.key}">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="startEvent1">
        <omgdc:Bounds x="173" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

      this.bpmnModeler.importXML(diagramXML).then(() => {
        const canvas = this.bpmnModeler.get('canvas')
        canvas.zoom('fit-viewport')
      }).catch(err => {
        console.error(err)
      })
    },
    // Sync input name/key to BPMN model
    updateProcessInfo() {
      if (!this.info.key || !this.info.name) return

      try {
        const rootElement = this.bpmnModeler.get('canvas').getRootElement()
        const businessObject = rootElement.businessObject

        if (businessObject.$type === 'bpmn:Process') {
          businessObject.id = this.info.key
          businessObject.name = this.info.name
        } else {
          const definitions = this.bpmnModeler.getDefinitions()
          if (definitions && definitions.rootElements) {
            const process = definitions.rootElements.find(e => e.$type === 'bpmn:Process')
            if (process) {
              process.id = this.info.key
              process.name = this.info.name
            }
          }
        }
      } catch (e) {
        console.error(e)
      }
    },
    async handleSave() {
      if (!this.info.name || !this.info.key) {
        this.$message.error('请输入流程名称和标识(Key)')
        return
      }
      this.updateProcessInfo()

      try {
        const result = await this.bpmnModeler.saveXML({ format: true })
        const { xml } = result

        this.loading = true
        // If we have a modelId, saveModel will update it (based on Key or ID passed?
        // Backend uses Key to find latest version, or creates new.
        // Since we are editing a specific Model, we should ideally pass ID to update THAT model.
        // But the previous implementation relies on Key.
        // Let's rely on the backend logic I implemented:
        // `repositoryService.createModelQuery().modelKey(key).latestVersion().singleResult();`
        // If I change the KEY, it creates a NEW model. That might be unintended if renaming.
        // But for now, let's stick to the simple flow.

        import('@/api/flowable/definition').then(({ saveModel }) => {
          saveModel({
            name: this.info.name,
            key: this.info.key,
            xml: xml
          }).then(res => {
            this.modelId = res
            this.$notify({ title: '成功', message: '保存成功', type: 'success' })
            this.loading = false
          }).catch(() => { this.loading = false })
        })
      } catch (err) {
        this.loading = false
        console.error(err)
      }
    },
    async handleDeploy() {
      if (!this.info.name || !this.info.key) {
        this.$message.error('请输入流程名称和标识(Key)')
        return
      }
      this.updateProcessInfo()

      try {
        const result = await this.bpmnModeler.saveXML({ format: true })
        const { xml } = result

        this.loading = true

          import('@/api/flowable/definition').then(({ saveModel, deployModel }) => {
            // 1. Save Model first
            saveModel({
              name: this.info.name,
              key: this.info.key,
              xml: xml
            }).then(res => {
              this.modelId = res // Update ID

              // 2. Deploy Model
              deployModel(this.modelId).then(() => {
                this.$notify({ title: '成功', message: '发布成功', type: 'success' })
                this.loading = false
                this.back()
              }).catch(() => { this.loading = false })
            }).catch(() => { this.loading = false })
          })
      } catch (err) {
        this.loading = false
        console.error(err)
      }
    },
    async download() {
      if (!this.info.name || !this.info.key) {
        this.$message.error('请输入流程名称和标识(Key)')
        return
      }
      this.updateProcessInfo()

      try {
        const result = await this.bpmnModeler.saveXML({ format: true })
        const { xml } = result
        const blob = new Blob([xml], { type: 'application/xml' })
        const link = document.createElement('a')
        link.href = window.URL.createObjectURL(blob)
        link.download = this.info.name + '.bpmn20.xml'
        link.click()
      } catch (err) {
        console.error(err)
      }
    },
    back() {
      this.$router.push('/flowable/definition')
    }
  }
}
</script>

<style lang="scss" scoped>
.modeler-container {
  display: flex;
  background-color: #ffffff;
  width: 100%;
  height: calc(100vh - 150px);
}
.canvas {
  flex: 1;
  height: 100%;
}
.properties-panel {
  width: 300px;
  border-left: 1px solid #ccc;
  padding: 10px;
  background: #f8f8f8;
  overflow-y: auto;
}
.panel-header {
  font-weight: bold;
  font-size: 16px;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}
</style>

