//Team.kt
package org.example.mmorpg

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

data class TeamMember(
    val playerId: Long,
    val playerName: String,
    val atk: Long,
    val luck: Long,
    val isVirtual: Boolean = false,
    val virtualClass: String? = null
)

data class Team(
    val captainId: Long,
    val groupId: Long,
    val members: MutableList<TeamMember> = mutableListOf(),
    val createTime: Long = System.currentTimeMillis(),
    var dungeonId: Int? = null,
    var captcha: String = ""
)

object TeamManager {
    private val teams: MutableMap<Long, Team> = ConcurrentHashMap()
    private val playerTeams: MutableMap<Long, Long> = ConcurrentHashMap()

    fun createTeam(captainId: Long, groupId: Long, captainName: String, atk: Long, luck: Long): Pair<Boolean, String> {
        if (teams.containsKey(groupId) || playerTeams.containsKey(captainId)) {
            return Pair(false, "")
        }

        val captcha = String.format("%02d", Random.nextInt(1, 100))
        val team = Team(captainId, groupId)
        team.captcha = captcha
        team.members.add(TeamMember(captainId, captainName, atk, luck))
        teams[groupId] = team
        playerTeams[captainId] = groupId

        return Pair(true, captcha)
    }

    fun verifyCaptcha(groupId: Long, captcha: String): Boolean {
        val team = teams[groupId] ?: return false
        return team.captcha == captcha
    }

    fun addMember(groupId: Long, playerId: Long, playerName: String, atk: Long, luck: Long): Boolean {
        val team = teams[groupId] ?: return false
        if (team.members.size >= 4 || playerTeams.containsKey(playerId)) {
            return false
        }

        team.members.add(TeamMember(playerId, playerName, atk, luck))
        playerTeams[playerId] = groupId
        return true
    }

    fun getTeamByGroup(groupId: Long): Team? {
        return teams[groupId]
    }

    fun getTeamByPlayer(playerId: Long): Team? {
        val groupId = playerTeams[playerId] ?: return null
        return teams[groupId]
    }

    fun disbandTeam(groupId: Long) {
        val team = teams[groupId] ?: return
        team.members.forEach { member ->
            if (!member.isVirtual) {
                playerTeams.remove(member.playerId)
            }
        }
        teams.remove(groupId)
    }

    fun isPlayerInTeam(playerId: Long): Boolean {
        return playerTeams.containsKey(playerId)
    }

    fun removeMember(playerId: Long): Boolean {
        val groupId = playerTeams[playerId] ?: return false
        val team = teams[groupId] ?: return false
        if (team.captainId == playerId) {
            disbandTeam(groupId)
            return true
        } else {
            team.members.removeIf { it.playerId == playerId }
            playerTeams.remove(playerId)
            return true
        }
    }

    fun checkExpiredTeams() {
        val currentTime = System.currentTimeMillis()
        val expiredTeams = teams.filter { currentTime - it.value.createTime > 5 * 60 * 1000 }
        expiredTeams.keys.forEach { groupId ->
            disbandTeam(groupId)
        }
    }

    fun autoFillTeam(groupId: Long, captainId: Long, topPlayerATK: Long, topPlayerLUCK: Long, captainATK: Long, captainLUCK: Long): Boolean {
        val team = teams[groupId] ?: return false

        if (team.captainId != captainId) {
            PluginMain.logger.info("自动填补失败：不是队长")
            return false
        }

        if (team.members.size >= 4) {
            PluginMain.logger.info("自动填补失败：队伍已满")
            return false
        }

        val virtualClasses = listOf("牧师S", "诗人S", "盗贼S", "猎手S")
        val neededMembers = 4 - team.members.size

        PluginMain.logger.info("需要填补 $neededMembers 个虚拟队员")

        for (i in 1..neededMembers) {
            val virtualClass = virtualClasses.random()
            val baseVirtualATK = (topPlayerATK * 0.75).toLong()
            val baseVirtualLUCK = (topPlayerLUCK * 0.75).toLong()
            val virtualATK = min(baseVirtualATK, captainATK)
            val virtualLUCK = min(baseVirtualLUCK, captainLUCK)

            PluginMain.logger.info("创建虚拟队员 $i: ATK=$virtualATK (基础=$baseVirtualATK, 限制=$captainATK), LUCK=$virtualLUCK (基础=$baseVirtualLUCK, 限制=$captainLUCK)")

            val virtualId = (-i - 1000).toLong()
            val virtualName = "[??]辅助队员"

            val virtualMember = TeamMember(
                playerId = virtualId,
                playerName = virtualName,
                atk = virtualATK,
                luck = virtualLUCK,
                isVirtual = true,
                virtualClass = virtualClass
            )

            team.members.add(virtualMember)
            playerTeams[virtualId] = groupId
        }

        PluginMain.logger.info("自动填补完成，队伍现在有 ${team.members.size} 名成员")
        return true
    }

    fun getVirtualMembersCount(team: Team): Int {
        return team.members.count { it.isVirtual }
    }

    fun removeVirtualMembers(groupId: Long) {
        val team = teams[groupId] ?: return
        team.members.removeAll { it.isVirtual }
        playerTeams.entries.removeAll { entry ->
            entry.key < 0 && entry.value == groupId
        }
    }
}