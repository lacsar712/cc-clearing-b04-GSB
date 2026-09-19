<template>
  <div class="page">
    <h2 class="page-title">批次详情</h2>
    <p class="page-desc">查看批次状态、参与义务、净头寸，并可确认 settle</p>

    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button @click="load">刷新</el-button>
      <el-button
        v-if="auth.isOperator"
        type="success"
        :disabled="detail?.run?.status !== 'COMPLETED'"
        @click="openSettleDialog"
      >确认 Settle</el-button>
    </div>

    <div class="card-panel" v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Run ID"><span class="mono">{{ detail.run.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.run.status)">
              {{ detail.run.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="交割日">{{ detail.run.settleDate }}</el-descriptions-item>
          <el-descriptions-item label="币种">{{ detail.run.currency }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.run.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="ΣnetAmount">{{ detail.sumNetAmount }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.run.failureReason" label="失败原因" :span="2">
            {{ detail.run.failureReason }}
          </el-descriptions-item>
          <template v-if="detail.run.status === 'SETTLED'">
            <el-descriptions-item label="Settle 时间">{{ formatTime(detail.run.settledAt) }}</el-descriptions-item>
            <el-descriptions-item label="操作员">{{ detail.run.settledBy }}</el-descriptions-item>
            <el-descriptions-item label="Settle 备注" :span="2">{{ detail.run.settleNote }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <h3 style="margin:20px 0 10px">净头寸</h3>
        <el-table :data="detail.positions" stripe>
          <el-table-column prop="memberId" label="会员 ID" min-width="220">
            <template #default="{ row }"><span class="mono">{{ row.memberId }}</span></template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="90" />
          <el-table-column prop="netAmount" label="净头寸" min-width="160" />
        </el-table>

        <h3 style="margin:20px 0 10px">参与义务</h3>
        <el-table :data="detail.obligations" stripe>
          <el-table-column prop="obligationId" label="义务 ID" min-width="200">
            <template #default="{ row }"><span class="mono">{{ row.obligationId }}</span></template>
          </el-table-column>
          <el-table-column prop="payerMemberId" label="付款方" min-width="180" />
          <el-table-column prop="payeeMemberId" label="收款方" min-width="180" />
          <el-table-column prop="amount" label="金额" width="140" />
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
      </template>
    </div>

    <el-dialog v-model="settleDialogVisible" title="确认 Settle" width="480px" :close-on-click-modal="false">
      <p style="margin:0 0 12px;color:var(--muted)">
        将对批次 <span class="mono">{{ detail?.run?.runId }}</span> 执行结算，全部参与义务将置为 SETTLED，操作不可撤销。
      </p>
      <el-input
        v-model="settleNote"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="必填：请填写 settle 备注（如结算依据、复核人）"
      />
      <p v-if="!settleNote.trim()" style="margin:8px 0 0;color:var(--el-color-danger);font-size:12px">
        备注不能为空
      </p>
      <template #footer>
        <el-button @click="settleDialogVisible = false">取消</el-button>
        <el-button
          type="success"
          :disabled="!settleNote.trim()"
          :loading="settling"
          @click="confirmSettle"
        >确认 Settle</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const settling = ref(false)
const detail = ref(null)
const settleDialogVisible = ref(false)
const settleNote = ref('')

function statusType(s) {
  if (s === 'COMPLETED' || s === 'SETTLED') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/netting-runs/${route.params.id}`)
    detail.value = data
  } finally {
    loading.value = false
  }
}

function openSettleDialog() {
  settleNote.value = ''
  settleDialogVisible.value = true
}

async function confirmSettle() {
  const note = settleNote.value.trim()
  if (!note) {
    ElMessage.error('settle 备注不能为空')
    return
  }
  settling.value = true
  try {
    await api.post(`/netting-runs/${route.params.id}/settle`, { note })
    settleDialogVisible.value = false
    ElMessage.success('Settle 完成，义务已 SETTLED')
    await load()
  } finally {
    settling.value = false
  }
}

onMounted(load)
</script>
