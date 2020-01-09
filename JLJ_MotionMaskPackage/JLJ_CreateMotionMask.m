% JLJ
% This is the most up to date mask procedure I have!!!

% Combined motion model.  This script creates a simple background model
% (foreground is assumed to be a small portion of time history of each
% pixel) and then creates a motion model which emphasizes only areas of
% change in the background model (when pixel switches from
% foreground/background or vice versa).  This script then computes the
% intersection between the two masks and outputs the binary video and/or
% the grayscale masked video.

clc
clear all
close all

%--------------------------------------------------------------------------
% User Settings
%--------------------------------------------------------------------------
% Processing Options ----
MedianFiltering = false; % use matlab's 3x3x3 median filter to address noise
                         % on grayscale video before further processing

MakeSidewaysVideo=false; % specifies to make video in spatial dimension

MakeBinaryVid_Full = true; % Make binary vid of combined model
                         
MakeBinaryVid_FM = false; % Make binary vid  of foreground model
                         
MakeBinaryVid_MM = false; % Make binary vid of motion model

MakeMaskedVideo = false;  % specifies whether to create a video showing a 
                         % binary mask on the original grayscale video
                         
MakeIndividualMaskedVideos = false; % this script AND's two masks, this setting
                                   % makes videos of each mask

%--------------------------
% Density Filter Options -----
Filter2D.bool = false;
Filter2D.size = [20 20];
Filter2D.thresh = 80;

Filter3D.bool = true;
Filter3D.size = [5 5 5];
Filter3D.thresh = 13;

% ---------------------------
% Post AND: Filter Options
PostFilt2D.bool = true;
PostFilt2D.size = [20 20];
PostFilt2D.thresh = 80;

PostFilt3D.bool = false;
PostFilt3D.size = [5 5 5];
PostFilt3D.thresh = 13;

PostFiltSum.bool = false;
PostFiltSum.tConst = 3; % num frames to OR results over...sum(frame(i):frame(i+tConst))
% ---------------------------

% These are the videos that will be processed (minus the '.mp4')
% RootPath = 'C:\Users\Joachim\Google Drive\TubeStuff\TestVideos\';

RootPath = 'C:\Users\f002tj9\Documents\Research\JLJ_MotionMaskPackage\';
VideoRootNames  = {'ski1','ski2'};%,...
% VideoRootNames  = {'VIRAT_S_050000_01_000207_000361'};%,...
%                    'VIRAT_S_050300_04_001057_001122',...
%                    'VIRAT_S_010200_01_000254_000322',...
%                    'VIRAT_S_010000_04_000530_000605'};
% VideoRootNames  = {'virat1',...
%                    };
% Lasiesta indoor scenes
% VideoRootNames  = {'I_BS_01',...
%                    'I_BS_02',...
%                    'I_OC_01',...
%                    'I_OC_02',...
%                    'I_SI_01',...
%                    'I_SI_02',...
%                    'O_CL_01',...
%                    'O_CL_02',...
%                    'O_RA_01',...
%                    'O_RA_02',...
%                    'O_SN_01',...
%                    'O_SN_02',...
%                    'O_SU_01',...
%                    'O_SU_02'};
%--------------------------------------------------------------------------
%--------------------------------------------------------------------------
           
for i=1:length(VideoRootNames)
    TotalTime = tic;
    % Read video and convert to grayscale------------------
    tic
    [gF] = ConvertVidToGray(sprintf('%s%s.mp4',RootPath,VideoRootNames{i}),0,1,inf);
    ReadGrayTime = toc
    % Median Filter on Grayscale Video --------------
    if MedianFiltering == true
        tic
        OutputName = 'PreGMed3D';
        gF = medfilt3(gF);
        MedianFilt3Time = toc
    else
        OutputName = '';
    end
    %------------------------------------------------------
    % Camera Motion Mask ------------------
%     [bF_Foreground] = CreateBasicCameraMotionMask(gF, 2, 20);
%     ForegroundName = 'CameraMotion';
    
    %------------------------------------------------------
    % Build Motion Mask
    ForegroundThresh = 5;
    Filter3DFirst = true;
    [bF_Motion, MotionName] = FilteredMotionModel(gF, ForegroundThresh, Filter3DFirst, Filter2D, Filter3D);
    % Build Foreground Mask
    ForegroundThresh = 10;
    Filter3DFirst = true;
    [bF_Foreground, ForegroundName] = FilteredForegroundModel(gF, ForegroundThresh, Filter3DFirst, Filter2D, Filter3D);
    %------------------------------------------------------
    % Find Intersection of Masks (AND)
    bF = bF_Motion & bF_Foreground;
    % For tests only
    bF = uint8(255*bF);
    %------------------------------------------------------
    % POST Density Filtering
    OutputName = sprintf('%s_%s_AND_%s',OutputName, MotionName, ForegroundName);
    if PostFilt2D.bool == true || PostFilt3D.bool == true
        [bF, FilterName] = ApplyDensityFilters(bF, false, Filter3DFirst, PostFilt2D, PostFilt3D);
        OutputName = sprintf('%s_Post_%s',OutputName, FilterName);
    end
    % POST Summing Procedure ---------
    if PostFiltSum.bool == true
        OutputName = sprintf('%s_Post_Sum%d',OutputName, PostFiltSum.tConst);
        [bF] = OR_Frames(bF,PostFiltSum.tConst);
    end
    %------------------------------------------------------
    toc(TotalTime) % Record the total time for operations
    OutputName = sprintf('OutputVideos\\%s_%s',VideoRootNames{i},OutputName);
    % Make Video -------------------------------------------------
    if MakeBinaryVid_Full == true
        Name = sprintf('%s_Binary.mp4',OutputName);
        NewVid = VideoWriter(Name,'MPEG-4'); % video handle
        NewVid.FrameRate = 30;
        open(NewVid);
        for j=1:length(bF(1,1,:))
            writeVideo(NewVid, uint8(bF(:,:,j)))
        end
        close(NewVid)
    end
    %--------------------------------------------------------------
    
    if MakeMaskedVideo == true
        Name = sprintf('%s_Masked.mp4',OutputName);
        OverlayMaskOnGrayScaleVideo(gF,uint8(bF),Name);
        % only make individual videos if specified
        if MakeIndividualMaskedVideos == true
            Name = sprintf('OutputVideos\\%s_%s_MaskedMM.mp4',VideoRootNames{i},MotionName);
            OverlayMaskOnGrayScaleVideo(gF,uint8(bF_Motion),Name);
            Name = sprintf('OutputVideos\\%s_%s_MaskedFM.mp4',VideoRootNames{i},ForegroundName);
            OverlayMaskOnGrayScaleVideo(gF,uint8(bF_Foreground),Name);            
        end
    end
    if MakeSidewaysVideo == true
        bF = permute(bF,[1,3,2]); % put in x,t,y style
        Name = sprintf('%s_Sideways.mp4',OutputName);
        NewVid = VideoWriter(Name,'MPEG-4'); % video handle
        NewVid.FrameRate = 30;
        open(NewVid);
        for j=1:length(bF(1,1,:))
            writeVideo(NewVid, uint8(bF(:,:,j)))
        end
        close(NewVid)
    end
    if MakeBinaryVid_FM == true
        Name = sprintf('OutputVideos\\%s_Binary.mp4',ForegroundName);
        NewVid = VideoWriter(Name,'MPEG-4'); % video handle
        NewVid.FrameRate = 30;
        open(NewVid);
        for j=1:length(bF_Foreground(1,1,:))
            writeVideo(NewVid, uint8(bF_Foreground(:,:,j)))
        end
        close(NewVid)
    end
    if MakeBinaryVid_MM == true
        Name = sprintf('OutputVideos\\%s_Binary.mp4',MotionName);
        NewVid = VideoWriter(Name,'MPEG-4'); % video handle
        NewVid.FrameRate = 30;
        open(NewVid);
        for j=1:length(bF_Motion(1,1,:))
            writeVideo(NewVid, uint8(bF_Motion(:,:,j)))
        end
        close(NewVid)
    end
%     clear gF bF
end
%--------------------------------------------------------------------------
%--------------------------------------------------------------------------
function [bF, ForegroundName] = FilteredForegroundModel(gF, ForegroundThresh, Filter3DFirst, Filter2D, Filter3D)
    % NOTE: this function expects these structs as inputs ------------
    % Filter2D.bool = true;
    % Filter2D.size = [20 20]
    % Filter2D.thresh = 80;
    % 
    % Filter3D.bool = true;
    % Filter3D.size = [5 5 5]
    % Filter3D.thresh = 13;
    %----------------------------------------------
    % Identify Discontinuities --------------------
    bF = CreateBasicBackgroundMask(gF,ForegroundThresh*255/100,0.2);
    bF = uint8(255*bF); % put back in x,y,t style
    %----------------------------------------------
    % Apply Density Filters -----------------------
    [bF, FilterName] = ApplyDensityFilters(bF, false, Filter3DFirst , Filter2D, Filter3D);
    %----------------------------------------------
    % Build name describing filtering options applied ---------------------
    % FM - ForegroundModel
    ForegroundName = sprintf('FM_%s',FilterName);
end
%--------------------------------------------------------------------------
% This function compartmentalizes the creation of the motion mask that is
% based on detecting/highlighting changes in a pixel value from background
% to foreground or vice versa.  The change threshold is ForegroundThresh,
% but has been kept constant at 20 for a while.  The changes are found via
% a sobel filter.
function [bF, MotionName] = FilteredMotionModel(gF, ForegroundThresh, Filter3DFirst, Filter2D, Filter3D)
    % -----------------------------------------------
    %------------------------------------------------------
    % Identify Discontinuities ----
    [bF] = CreateBasicMotionModel(gF, ForegroundThresh);    
    %----------------------------------------------
    % Apply Density Filters -----------------------
    [bF, FilterName] = ApplyDensityFilters(bF, false, Filter3DFirst , Filter2D, Filter3D);
    %----------------------------------------------
    % Build name describing filtering options applied ---------------------
    % FM - ForegroundModel
    MotionName = sprintf('MM_%s',FilterName);

end
