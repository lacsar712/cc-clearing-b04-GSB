<template>
  <div class="page">
    <h2 class="page-title">批次详情</h2>
    <p class="page-desc">查看批次状态、参与义务、净头寸，并可确认 settle</p>

    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button @click="load">刷新</el-button>
      <el-button
        v-if="auth.isOperator && detail?.run?.status === 'COMPLETED'"
        type="success"
        :loading="settling"
        @click="openSettleDialog"
      >确认 Settle</el-button>
    </div>

    <div class="card-panel" v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Run ID"><span class="mono">{{ detail.run.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.run.status)">{{ detail.run.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="交割日">{{ detail.run.settleDate }}</el-descriptions-item>
          <el-descriptions-item label="币种">{{ detail.run.currency }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.run.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="ΣnetAmount">{{ detail.sumNetAmount }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.run.status === 'SETTLED'" label="Settle 时间">
            {{ formatTime(detail.run.settledAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.run.status === 'SETTLED'" label="Settle 操作员">
            <span class="mono">{{ detail.run.settledBy }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.run.status === 'SETTLED'" label="Settle 备注" :span="2">
            {{ detail.run.settleRemark }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.run.failureReason" label="失败原因" :span="2">
            {{ detail.run.failureReason }}
          </el-descriptions-item>
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

    <el-dialog
      v-model="dialogVisible"
      title="确认 Settle 批次"
      width="520px"
      :close-on-click-modal="false"
      @closed="remark = ''"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
        title="Settle 后批次与义务状态不可回退，请二次确认。"
      />
      <div style="margin-bottom:6px">
        批次 <span class="mono">{{ detail?.run?.runId }}</span>
        （{{ detail?.run?.settleDate }} {{ detail?.run?.currency }}）
      </div>
      <el-form @submit.prevent>
        <el-form-item label="Settle 备注" required>
          <el-input
            v-model="remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="必填：请填写结算依据/确认说明，空备注无法提交"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="success"
          :loading="settling"
          :disabled="!remark.trim()"
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

const dialogVisible = ref(false)
const remark = ref('')

function statusTagType(status) {
  if (status === 'SETTLED') return 'success'
  if (status === 'COMPLETED') return 'warning'
  if (status === 'FAILED') return 'danger'
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
  remark.value = ''
  dialogVisible.value = true
}

async function confirmSettle() {
  // 前端必填校验：空白备注一律不放行
  if (!remark.value.trim()) {
    ElMessage.warning('Settle 备注为必填项')
    return
  }
  settling.value = true
  try {
    await api.post(`/netting-runs/${route.params.id}/settle`, { remark: remark.value.trim() })
    ElMessage.success('Settle 完成，义务已 SETTLED')
    dialogVisible.value = false
    await load()
  } finally {
    settling.value = false
  }
}

onMounted(load)
</script>
