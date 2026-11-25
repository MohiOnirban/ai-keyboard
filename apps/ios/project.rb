require 'xcodeproj'
Dir.chdir(__dir__)
project = Xcodeproj::Project.new('KothaKeys.xcodeproj')
app = project.new_target(:application, 'KothaKeys', :ios, '17.0')
ext = project.new_target(:app_extension, 'Keyboard', :ios, '17.0')
app.add_dependency(ext)
embed = app.new_copy_files_build_phase('Embed Keyboard')
embed.dst_subfolder_spec = '13'
embed.add_file_reference(ext.product_reference).settings = {'ATTRIBUTES'=>['RemoveHeadersOnCopy']}
common = project.main_group.new_group('Common','Common')
shared = Dir['Common/*.swift'].map{|f| common.new_file(File.basename(f))}
privacy = common.new_file('PrivacyInfo.xcprivacy')
rules = project.main_group.new_file('../../shared/phonetic.json')
branding = project.main_group.new_file('../../shared/branding/Assets.xcassets')
app.resources_build_phase.add_file_reference(branding)
app.build_configurations.each do |configuration|
  configuration.build_settings['ASSETCATALOG_COMPILER_APPICON_NAME'] = 'AppIcon'
  configuration.build_settings['ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME'] = 'AccentColor'
end
[[app,'App','dev.kothakeys.app'],[ext,'Keyboard','dev.kothakeys.app.keyboard']].each do |target,dir,bundle|
  group = project.main_group.new_group(dir,dir)
  target.add_file_references(shared + Dir[dir+'/*.swift'].map{|f|group.new_file(File.basename(f))})
  target.resources_build_phase.add_file_reference(rules)
  target.resources_build_phase.add_file_reference(privacy)
  %w[avro-lib.js LICENSE.txt NOTICE.txt].each do |name|
    ref = project.main_group.find_file_by_path('../../shared/avro/'+name) || project.main_group.new_file('../../shared/avro/'+name)
    target.resources_build_phase.add_file_reference(ref)
  end
  target.build_configurations.each do |c|
    c.build_settings.merge!('PRODUCT_BUNDLE_IDENTIFIER'=>bundle,'INFOPLIST_FILE'=>dir+'/Info.plist','CODE_SIGN_ENTITLEMENTS'=>'Kotha.entitlements','SWIFT_VERSION'=>'5.0','TARGETED_DEVICE_FAMILY'=>'1,2','CURRENT_PROJECT_VERSION'=>'1','MARKETING_VERSION'=>'0.1','LD_RUNPATH_SEARCH_PATHS'=>'$(inherited) @executable_path/Frameworks @executable_path/../../Frameworks')
    c.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS']='$(inherited) DEBUG' if c.name=='Debug'
  end
end
ext.build_configurations.each{|c| c.build_settings['APPLICATION_EXTENSION_API_ONLY']='YES';c.build_settings['SKIP_INSTALL']='YES'}
test = project.new_target(:ui_test_bundle, 'KothaUITests', :ios, '17.0')
test.add_dependency(app)
group = project.main_group.new_group('Tests','Tests')
test.add_file_references([group.new_file('CompanionTests.swift')])
test.build_configurations.each do |c|
  c.build_settings.merge!('TEST_TARGET_NAME'=>'KothaKeys','PRODUCT_BUNDLE_IDENTIFIER'=>'dev.kothakeys.uitests','GENERATE_INFOPLIST_FILE'=>'YES','SWIFT_VERSION'=>'5.0')
end
project.save
project.recreate_user_schemes
scheme = Xcodeproj::XCScheme.new
scheme.configure_with_targets(app,test,launch_target:true)
scheme.save_as(project.path,'KothaKeys',true)
