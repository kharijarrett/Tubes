function [bF, FilterName] = ApplyDensityFilters(bF, Inverse, Filter3DFirst, Filter2D, Filter3D)
%--------------------------------------------------------------------------
% This function compartmentalizes the two step filtering process that I use
% in a few places.  One can specify whether to do 2D or 3D filtering first
% as well as whether to do one or both filters.
% Inverse = false -> if density > thresh, then true: we want high density
%         = true  -> if density < thresh, then true: we want low density
    % Creates part of name below ------------------------------------------
    if Filter3D.bool == false
        Opt1 = '';
    else
        Opt1 = sprintf('Density3D%dx%dx%d_%dp',...
                            Filter3D.size(1),Filter3D.size(2),Filter3D.size(3),...
                            round(Filter3D.thresh*100/prod(Filter3D.size)));
    end
    if Filter2D.bool == false
        Opt2 = '';
    else
        Opt2 = sprintf('Density2D%dx%d_%dp',...
                            Filter2D.size(1),Filter2D.size(2),...
                            round(Filter2D.thresh*100/prod(Filter2D.size)));
    end
    %----------------------------------------------------------------------
    if Filter3DFirst == true
        % Density Filtering in 3D Space ---------
        if Filter3D.bool == true
            %------------------------------------------------------
            tic
            bF = double(logical(bF)); % expect input in x,y,t dimensions
            AvgFilter = ones(Filter3D.size)./prod(Filter3D.size);
            bF = imfilter(bF,AvgFilter);
            if Inverse == false
                bF = bF > Filter3D.thresh/prod(Filter3D.size);
            else
                bF = bF < Filter3D.thresh/prod(Filter3D.size);
            end
            bF = uint8(255*bF);
            DensityFiltTime3D = toc            
            %------------------------------------------------------
        end
        % Density Filtering in XY Space ---------
        if Filter2D.bool == true
            %------------------------------------------------------
            tic
            bF = double(logical(bF)); % expect input in x,y,t dimensions
            AvgFilter = ones(Filter2D.size)./prod(Filter2D.size);
            bF = imfilter(bF,AvgFilter);
            if Inverse == false
                bF = bF > Filter2D.thresh/prod(Filter2D.size);
            else
                bF = bF < Filter2D.thresh/prod(Filter2D.size);
            end
            bF = uint8(255*bF);
            DensityFiltTime2D = toc            
            %------------------------------------------------------
        end
        % Build name describing filtering options applied ---------------------
        FilterName = sprintf('%s%s',Opt1,Opt2);
    else
        % Density Filtering in XY Space ---------
        if Filter2D.bool == true
            %------------------------------------------------------
            tic
            bF = double(logical(bF)); % expect input in x,y,t dimensions
            AvgFilter = ones(Filter2D.size)./prod(Filter2D.size);
            bF = imfilter(bF,AvgFilter);
            if Inverse == false
                bF = bF > Filter2D.thresh/prod(Filter2D.size);
            else
                bF = bF < Filter2D.thresh/prod(Filter2D.size);
            end
            bF = uint8(255*bF);
            DensityFiltTime2D = toc            
            %------------------------------------------------------
        end
        % Density Filtering in 3D Space ---------
        if Filter3D.bool == true
            %------------------------------------------------------
            tic
            bF = double(logical(bF)); % expect input in x,y,t dimensions
            AvgFilter = ones(Filter3D.size)./prod(Filter3D.size);
            bF = imfilter(bF,AvgFilter);
            if Inverse == false
                bF = bF > Filter3D.thresh/prod(Filter3D.size);
            else
                bF = bF < Filter3D.thresh/prod(Filter3D.size);
            end
            bF = uint8(255*bF);
            DensityFiltTime3D = toc            
            %------------------------------------------------------
        end
        % Build name describing filtering options applied ---------------------
        FilterName = sprintf('%s%s',Opt2,Opt1);
    end
end
