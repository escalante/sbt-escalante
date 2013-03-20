#!/usr/bin/python

# -*- coding: utf-8; -*-
#
# Copyright 2012 Red Hat, Inc. and/or its affiliates.
#
# Licensed under the Eclipse Public License version 1.0, available at
# http://www.eclipse.org/legal/epl-v10.html

import re
import sys
import os
from multiprocessing import Process
from utils import *
from xml.etree.ElementTree import ElementTree

#noinspection PyBroadException
try:
    from argparse import ArgumentParser
except:
    prettyprint('''
        Welcome to the Escalante SBT Plugin Release Script.
        This release script requires that you use at least Python 2.7.0.
        It appears that you do not have the collections.Counter available,
        which are available by default in Python 2.7.0.
        ''', Levels.FATAL)
    sys.exit(1)

modules = []
uploader = None
git = None


def help_and_exit():
    prettyprint('''
        Welcome to the Escalante SBT Plugin Release Script.
        
%s        Usage:%s
        
            $ bin/release.py <sbt-escalante-version> -e <escalante-version>
            
%s        E.g.,%s
        
            $ bin/release.py 0.1.0 -e 0.2.0 %s<-- this will tag off master.%s
            
    ''' % (
    Colors.yellow(), Colors.end_color(), Colors.yellow(), Colors.end_color(),
    Colors.green(), Colors.end_color()),
                Levels.INFO)
    sys.exit(0)


def validate_version(version):
    version_pattern = get_version_pattern()
    if version_pattern.match(version):
        return version.strip().upper()
    else:
        prettyprint("Invalid version '" + version + "'!\n", Levels.FATAL)
        help_and_exit()


def switch_to_tag_release(branch):
    if git.remote_branch_exists():
        git.switch_to_branch()
        git.create_tag_branch()
    else:
        prettyprint(
            "Branch %s cannot be found on upstream repository.  Aborting!"
            % branch, Levels.FATAL)
        sys.exit(100)

def get_build_sbt_files_to_patch(working_dir):
    # Look for build.sbt files
    build_sbt_to_patch = []
    # Skip root build.sbt file which is treated differently
    skip = [working_dir + "/build.sbt"]
    for build_sbt_file in GlobDirectoryWalker(working_dir, 'build.sbt'):
        if build_sbt_file not in skip:
            build_sbt_to_patch.append(build_sbt_file)

    return build_sbt_to_patch


def update_version(base_dir, version):
    os.chdir(base_dir)
    build_sbt = "./build.sbt"
    readme_md = "./README.md"

    pieces = re.compile('[\.\-]').split(version)

    # 1. Update SBT plugin and Escalante versions in root build file
    f_in = open(build_sbt)
    f_out = open(build_sbt + ".tmp", "w")
    re_version = re.compile('\s*version := ')
    try:
        for l in f_in:
            if re_version.match(l):
                prettyprint("Update %s to version %s"
                            % (build_sbt, version), Levels.DEBUG)
                f_out.write('version := "%s"\n' % version)
            else:
                f_out.write(l)
    finally:
        f_in.close()
        f_out.close()

    # 2. Update SBT plugin versions in test files and README file
    require_version_update = get_build_sbt_files_to_patch(base_dir)
    require_version_update.insert(0, readme_md)
    update_sbt_plugin_version(version, require_version_update)
#    for f in require_version_update:
#        f_in = open(f)
#        f_out = open(f + ".tmp", "w")
#        re_version = re.compile('\s*addSbtPlugin\\("io.escalante.sbt"')
#        try:
#            for l in f_in:
#                if re_version.match(l):
#                    prettyprint("Update %s to version %s"
#                                % (f, version), Levels.DEBUG)
#                    f_out.write(
#                        '    addSbtPlugin("io.escalante.sbt" %% "sbt-escalante" %% "%s")\n'
#                        % version)
#                else:
#                    f_out.write(l)
#        finally:
#            f_in.close()
#            f_out.close()

    require_version_update.insert(0, build_sbt)
    for f in require_version_update:
        prettyprint("Rename back %s" % f, Levels.DEBUG)
        os.rename(f + ".tmp", f)

    # Now make sure this goes back into the repository.
    git.commit(require_version_update,
        "'Release Script: update SBT plugin version %s'" % version)

    # And return the next version - currently unused
    return pieces[0] + '.' + str(int(pieces[1]) + 1) + '.' + '0-SNAPSHOT'

def update_sbt_plugin_version(version, require_version_update):
    for f in require_version_update:
        f_in = open(f)
        f_out = open(f + ".tmp", "w")
        re_version = re.compile('\s*addSbtPlugin\\("io.escalante.sbt"')
        try:
            for l in f_in:
                if re_version.match(l):
                    prettyprint("Update %s to version %s"
                                % (f, version), Levels.DEBUG)
                    f_out.write(
                        '    addSbtPlugin("io.escalante.sbt" %% "sbt-escalante" %% "%s")\n'
                        % version)
                else:
                    f_out.write(l)
        finally:
            f_in.close()
            f_out.close()

def update_escalante_version(base_dir, escalante_version):
    os.chdir(base_dir)
    build_sbt = "./build.sbt"
    readme_md = "./README.md"
    plugin_scala = "./src/main/scala/io/escalante/sbt/EscalantePlugin.scala"

    pieces = re.compile('[\.\-]').split(escalante_version)

    # 1. Update SBT plugin and Escalante versions in root build file
    f_in = open(build_sbt)
    f_out = open(build_sbt + ".tmp", "w")
    re_esc_version = re.compile('\s*\"io.escalante\" ')
    try:
        for l in f_in:
            if re_esc_version.match(l):
                prettyprint("Update %s to Escalante version %s"
                            % (build_sbt, escalante_version), Levels.DEBUG)
                f_out.write('   "io.escalante" %% "escalante-dist" %% "%s" artifacts(Artifact("escalante-dist", "zip", "zip")),\n'
                            % escalante_version)
            else:
                f_out.write(l)
    finally:
        f_in.close()
        f_out.close()

    # 2. Update versions in README file
    update_escalante_version_readme(escalante_version, readme_md)
#    f_in = open(readme_md)
#    f_out = open(readme_md + ".tmp", "w")
#    re_esc_version = re.compile('\s*\* `escalanteVersion')
#    try:
#        for l in f_in:
#            if re_esc_version.match(l):
#                prettyprint("Update to Escalante version %s"
#                            % escalante_version, Levels.DEBUG)
#                f_out.write('* `escalanteVersion := "%s"`\n' % escalante_version)
#            else:
#                f_out.write(l)
#    finally:
#        f_in.close()
#        f_out.close()

    # 3. Update versions in Escalante plugin Scala class
    f_in = open(plugin_scala)
    f_out = open(plugin_scala + ".tmp", "w")
    re_esc_version = re.compile('\s*escalanteVersion :=')
    try:
        for l in f_in:
            if re_esc_version.match(l):
                prettyprint("Update %s to Escalante version %s"
                            % (build_sbt, escalante_version), Levels.DEBUG)
                f_out.write('    escalanteVersion := "%s",\n' % escalante_version)
            else:
                f_out.write(l)
    finally:
        f_in.close()
        f_out.close()

    modified_files = [build_sbt, readme_md, plugin_scala]
    os.rename(build_sbt + ".tmp", build_sbt)
    os.rename(readme_md + ".tmp", readme_md)
    os.rename(plugin_scala + ".tmp", plugin_scala)

    # Now make sure this goes back into the repository.
    git.commit(modified_files,
        "'Release Script: update Escalante version %s'" % escalante_version)

    # And return the next version - currently unused
    return pieces[0] + '.' + str(int(pieces[1]) + 1) + '.' + '0-SNAPSHOT'

def update_escalante_version_readme(escalante_version, readme_md):
    f_in = open(readme_md)
    f_out = open(readme_md + ".tmp", "w")
    re_esc_version = re.compile('\s*\* `escalanteVersion')
    try:
        for l in f_in:
            if re_esc_version.match(l):
                prettyprint("Update Escalante version %s"
                            % escalante_version, Levels.DEBUG)
                f_out.write('* `escalanteVersion := "%s"`\n' % escalante_version)
            else:
                f_out.write(l)
    finally:
        f_in.close()
        f_out.close()

def get_module_name(pom_file):
    tree = ElementTree()
    tree.parse(pom_file)
    return tree.findtext("./{%s}artifactId" % maven_pom_xml_namespace)


def do_task(target, args, async_processes):
    if settings.multi_threaded:
        async_processes.append(Process(target=target, args=args))
    else:
        target(*args)

### This is the starting place for this script.
def release():
    global settings
    global uploader
    global git
    assert_python_minimum_version(2, 5)

    parser = ArgumentParser()
    parser.add_argument('-d', '--dry-run', action='store_true', dest='dry_run',
                        help="release dry run", default=False)
    parser.add_argument('-v', '--verbose', action='store_true', dest='verbose',
                        help="verbose logging", default=True)
    parser.add_argument('-n', '--non-interactive', action='store_true',
                        dest='non_interactive',
                        help="non interactive script", default=False)
    parser.add_argument('-e', '--escalante-version', action='store', dest='escalante_version',
                        help="escalante version")
    parser.add_argument('-x', '--next-version', action='store', dest='next_version',
                        help="next sbt plugin version")

    # TODO Add branch...
    (settings, extras) = parser.parse_known_args()
    if len(extras) == 0:
        prettyprint("No release version given", Levels.FATAL)
        sys.exit(1)

    version = extras[0]
    interactive = not settings.non_interactive

    base_dir = os.getcwd()
    branch = "master"

    escalante_version = settings.escalante_version
    if escalante_version is None:
        prettyprint("No Escalante version given", Levels.FATAL)
        sys.exit(1)

#    next_version = settings.next_version
#    if next_version is None:
#        proceed = input_with_default(
#        'No next SBT plugin version given! Are you sure you want to proceed?', 'N')
#        if not proceed.upper().startswith('Y'):
#            prettyprint("... User Abort!", Levels.WARNING)
#            sys.exit(1)

    prettyprint(
        "Releasing Escalante SBT Plugin version %s for Escalante version %s from branch '%s'"
        % (version, escalante_version, branch), Levels.INFO)

    if interactive:
        sure = input_with_default("Are you sure you want to continue?", "N")
        if not sure.upper().startswith("Y"):
            prettyprint("... User Abort!", Levels.WARNING)
            sys.exit(1)

    prettyprint("OK, releasing! Please stand by ...", Levels.INFO)

    ## Set up network interactive tools
    if settings.dry_run:
        # Use stubs
        prettyprint(
            "*** This is a DRY RUN.  No changes will be committed.  Used to test this release script only. ***"
            , Levels.DEBUG)
        prettyprint("Your settings are %s" % settings, Levels.DEBUG)
        uploader = DryRunUploader()
    else:
        prettyprint("*** LIVE Run ***", Levels.DEBUG)
        prettyprint("Your settings are %s" % settings, Levels.DEBUG)
        uploader = Uploader(settings)

    git = Git(branch, version, settings)
    if interactive and not git.is_upstream_clone():
        proceed = input_with_default(
            'This is not a clone of an %supstream%s Escalante SBT plugin repository! Are you sure you want to proceed?' % (
            Colors.UNDERLINE, Colors.END), 'N')
        if not proceed.upper().startswith('Y'):
            prettyprint("... User Abort!", Levels.WARNING)
            sys.exit(1)

    ## Release order:
    # Step 1: Tag in Git
    prettyprint("Step 1: Tagging %s in git as %s" % (branch, version), Levels.INFO)
    switch_to_tag_release(branch)
    prettyprint("Step 1: Complete", Levels.INFO)

    # Step 2: Update version in tagged files
    prettyprint("Step 2: Updating version number", Levels.INFO)
    update_version(base_dir, version)
    update_escalante_version(base_dir, escalante_version)
    prettyprint("Step 2: Complete", Levels.INFO)

    # Step 3: Build and test in SBT
    prettyprint("Step 3: Build and publish", Levels.INFO)
    build_publish(settings)
    prettyprint("Step 3: Complete", Levels.INFO)

    async_processes = []

    ## Wait for processes to finish
    for p in async_processes:
        p.start()

    for p in async_processes:
        p.join()

    ## Tag the release
    git.tag_for_release()

    if not settings.dry_run:
        git.push_tags_to_origin()
        git.cleanup()

        # Set master README versions with latest released ones
        update_sbt_plugin_version(version, ["./README.md"])
        update_escalante_version_readme(escalante_version, "./README.md")

        git.commit(["./README.md"],
                   "'Release Script: update README file in master to last released version=%s and escalanteVersion=%s'"
                   % (version, escalante_version))
        git.push_master_to_origin()
    else:
        prettyprint(
            "In dry-run mode.  Not pushing tag to remote origin and not removing temp release branch %s." % git.working_branch
            , Levels.DEBUG)

#        # Update master with next version
#        next_version = settings.next_version
#        if next_version is not None:
#            # Update to next version
#            prettyprint("Step 4: Updating version number for next release", Levels.INFO)
#            update_version(base_dir, next_version)
#            git.push_master_to_origin()
#            prettyprint("Step 4: Complete", Levels.INFO)


if __name__ == "__main__":
    release()
