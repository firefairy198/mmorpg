//Team.kt
package org.example.mmorpg

import java.util.concurrent.ConcurrentHashMap

data class TeamMember(
    val playerId: Long,
    val playerName: String,
    val atk: Int,
    val luck: Int
)

data class Team(
    val captainId: Long,
    val groupId: Long,
    val members: MutableList<TeamMember> = mutableListOf(),
    val createTime: Long = System.currentTimeMillis(),
    var dungeonId: Int? = null
)

object TeamManager {
    private val teams: MutableMap<Long, Team> = ConcurrentHashMap() // key: groupId
    private val playerTeams: MutableMap<Long, Long> = ConcurrentHashMap() // key: playerId, value: groupId

    fun createTeam(captainId: Long, groupId: Long, captainName: String, atk: Int, luck: Int): Boolean {
        if (teams.containsKey(groupId) || playerTeams.containsKey(captainId)) {
            return false
        }

        val team = Team(captainId, groupId)
        team.members.add(TeamMember(captainId, captainName, atk, luck))
        teams[groupId] = team
        playerTeams[captainId] = groupId
        return true
    }

    fun addMember(groupId: Long, playerId: Long, playerName: String, atk: Int, luck: Int): Boolean {
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
        team.members.forEach { playerTeams.remove(it.playerId) }
        teams.remove(groupId)
    }

    fun isPlayerInTeam(playerId: Long): Boolean {
        return playerTeams.containsKey(playerId)
    }

    fun removeMember(playerId: Long): Boolean {
        val groupId = playerTeams[playerId] ?: return false
        val team = teams[groupId] ?: return false
        if (team.captainId == playerId) {
            // 队长离开，解散队伍
            disbandTeam(groupId)
            return true
        } else {
            // 队员离开，仅移除自己
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
}