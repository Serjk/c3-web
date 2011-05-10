package org.aphreet.c3.snippet

/**
 * Copyright (c) 2011, Dmitry Ivanov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *

 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the IFMO nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import net.liftweb.util.Helpers._
import xml.{Text, NodeSeq}
import org.aphreet.c3.plabaccess.PlabClient
import net.liftweb.http.js.JsCmds.Alert
import net.liftweb.http.js.{JsCmds, JsCmd}
import net.liftweb.common.{Full, Empty, Box}
import net.liftweb.http.{S, RequestVar, SHtml}
import com.vmware.vim25.mo.{HostSystem, VirtualMachine}

class VMServiceSnippet {

  private object selectedVMs extends RequestVar[scala.collection.mutable.HashSet[VirtualMachine]](scala.collection.mutable.HashSet())
  private object currentCommand extends RequestVar[Command](RebootSelectedVMs)

  abstract class Command {
    val name: String
  }
  case object RebootSelectedVMs extends Command {
    val name = "Reboot VMs"
  }
  case object StartupSelectedGuests extends Command {
    val name = "Startup Guests"
  }
  case object ShutdownSelectedGuests extends Command {
    val name = "Shutdown Guests"
  }

  val cmdList: List[Command] = List(RebootSelectedVMs,StartupSelectedGuests,ShutdownSelectedGuests)

  def viewVMs(html: NodeSeq): NodeSeq = {

    def doCommand(cmd: Command): JsCmd = {
      cmd match {
        case RebootSelectedVMs => rebootSelected
        case StartupSelectedGuests => startupSelected
        case ShutdownSelectedGuests => shutdownSelected
        case _ => Alert("Error: unknown action.")
      }
    }

    def shutdownSelected: JsCmd = {
          for (vm <- selectedVMs){
            if(vm.getRuntime.getPowerState.name == "poweredOn")
              vm.powerOffVM_Task()
          }
          Alert("Selected VMs have been powered off: "+selectedVMs.map(_.getName).mkString(", ")) & JsCmds.RedirectTo("")
        }


    def startupSelected: JsCmd = {
      for (vm <- selectedVMs){
        if(vm.getRuntime.getPowerState.name == "poweredOff")
          vm.powerOnVM_Task(null)
      }
      Alert("Selected VMs have been started: "+selectedVMs.map(_.getName).mkString(", ")) & JsCmds.RedirectTo("")
    }

    def rebootSelected: JsCmd = {
      for (vm <- selectedVMs){
        if(vm.getRuntime.getPowerState.name != "poweredOff")
          vm.rebootGuest()
      }
      Alert("Selected VMs have been rebooted: "+selectedVMs.map(_.getName).mkString(", ")) & JsCmds.RedirectTo("")
    }

    bind("vms", html,
      "list" -> ( (ns: NodeSeq) =>
         PlabClient.getVMs() flatMap( vm => bind("vm",ns, "name" -> vm.getName,
           "uptime" -> Box[Integer](vm.getSummary.quickStats.getUptimeSeconds).map(i => i.toString).openOr("unknown"),
           "vmware_tools" -> ( vm.getGuest.getToolsRunningStatus match {
             case _ => <img src="/images/icons/" /> // TODO
           } ),
           "memory_usage" -> {
             vm.getSummary.getQuickStats.getGuestMemoryUsage match {
               case null => "0"
               case mu => mu.toString
             }
           },
           "power_state" -> ( vm.getRuntime.getPowerState.name match {
             case "poweredOn" => <img src="/images/icons/circle_green.png" width="24" height="24" />
             case "poweredOff" => <img src="/images/icons/circle_red.png" width="24" height="24" />
             case _ => <img src="/images/icons/circle_orange.png" width="24" height="24" />
           } ),
           "select" -> SHtml.ajaxCheckbox(false, (checked) => {
               if(checked) selectedVMs += vm
               else selectedVMs -= vm
               JsCmds.Noop
           }),
           "reboot" -> SHtml.link("/index",() => vm.rebootGuest,Text("reboot"))
         )): NodeSeq
      ),
      "select_action" -> ( (ns: NodeSeq) =>
        SHtml.ajaxSelectObj[Command]( cmdList.map(cmd => (cmd, cmd.name) ), Full(RebootSelectedVMs), (c:Command) => { currentCommand.set(c); JsCmds.Noop } )
      ),
      "do_action" -> ( (ns: NodeSeq) => SHtml.ajaxButton(ns, () => doCommand(currentCommand.is) )  )
    )
  }


  def hostInfo(html: NodeSeq): NodeSeq = {

    PlabClient.getHost match {
      case Full(host: HostSystem) => {
        bind("host", html,
          "name" -> host.getName,
          "memory_available" -> (host.getHardware.getMemorySize / 1024 / 1024 ).toString,
          "server_model" -> host.getHardware.getSystemInfo.getModel,
          "vendor" -> host.getHardware.getSystemInfo.getVendor,
          "cpu_info" -> ( (ns: NodeSeq) =>
            bind("cpu", ns,
             "cores" -> host.getHardware.getCpuInfo.getNumCpuCores,
             "hz" -> host.getHardware.getCpuInfo.getHz,
             "threads" -> host.getHardware.getCpuInfo.getNumCpuThreads
            )
          )
        )
      }
      case _ => {
        S error "Host "+ PlabClient.hostname+" wasn't found"
        NodeSeq.Empty
      }
    }

  }



}